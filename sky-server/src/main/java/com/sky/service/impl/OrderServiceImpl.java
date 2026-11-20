package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.exception.StripeApiException;
import com.sky.mapper.*;
import com.sky.metrics.OrderMetrics;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.service.RefundService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.StripePayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    /** Clients send a UUID or similar; anything else is refused rather than stored. */
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StripePayUtil stripePayUtil;
    @Autowired
    private RefundService refundService;
    @Autowired
    private OrderMetrics orderMetrics;
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * Places an order from the signed-in customer's cart.
     * <p>
     * With an Idempotency-Key, a retried request (double click, timeout, lost response) returns the
     * order the first request created instead of placing another one. When two requests with the
     * same key run at the same time, the UNIQUE KEY (user_id, idempotency_key) lets exactly one insert
     * succeed; the other rolls back and returns the winner's order.
     */
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO, String idempotencyKey) {
        Long userId = BaseContext.getCurrentId();
        if (idempotencyKey == null) {
            return transactionTemplate.execute(status -> createOrder(ordersSubmitDTO, userId, null));
        }
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new OrderBusinessException(MessageConstant.IDEMPOTENCY_KEY_INVALID);
        }

        Orders existing = orderMapper.getByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing != null) {
            return toSubmitVO(existing);
        }
        try {
            return transactionTemplate.execute(status -> createOrder(ordersSubmitDTO, userId, idempotencyKey));
        } catch (DuplicateKeyException | ShoppingCartBusinessException e) {
            // A concurrent request with the same key committed first, and may already have emptied
            // the cart. Its transaction is finished, so this fresh read sees the order it created.
            existing = orderMapper.getByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing == null) {
                throw e;
            }
            return toSubmitVO(existing);
        }
    }

    private OrderSubmitVO createOrder(OrdersSubmitDTO ordersSubmitDTO, Long userId, String idempotencyKey) {
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        // Another customer's address is treated as missing.
        if (addressBook == null || !userId.equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }


        //checkOutOfRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if(shoppingCartList == null || shoppingCartList.size() == 0){

            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }


        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        // Cart prices were copied from the menu by the server when each item was added.
        orders.setAmount(cartTotal(shoppingCartList));
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(newOrderNumber());
        orders.setAddress(addressBook.getDetail());
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orders.setIdempotencyKey(idempotencyKey);

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();

        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);


        shoppingCartMapper.deleteByUserId(userId);
        orderMetrics.orderPlaced();

        return toSubmitVO(orders);
    }

    private static OrderSubmitVO toSubmitVO(Orders orders) {
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
    }

    /**
     * 32 hex characters from a random UUID. A timestamp gave two orders placed in the same
     * millisecond the same number; the UNIQUE KEY on orders.number now backs this up.
     */
    private static String newOrderNumber() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static BigDecimal cartTotal(List<ShoppingCart> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (ShoppingCart item : items) {
            total = total.add(item.getAmount().multiply(BigDecimal.valueOf(item.getNumber())));
        }
        return total;
    }

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;

    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address",shopAddress);
        map.put("output","json");
        map.put("ak",ak);


        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("Unable to resolve the restaurant address");
        }


        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");

        String shopLngLat = lat + "," + lng;

        map.put("address",address);

        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("Unable to resolve the delivery address");
        }


        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");

        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");


        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("Unable to calculate the delivery route");
        }


        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000){

            throw new OrderBusinessException("The address is outside the delivery area");
        }
    }

    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {

        Orders orders = orderMapper.getByNumberAndUserId(ordersPaymentDTO.getOrderNumber(), BaseContext.getCurrentId());
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }


        if (Orders.PAID.equals(orders.getPayStatus())) {
            throw new OrderBusinessException("This order has already been paid");
        }
        // A cancelled or timed-out order must not be paid for any more.
        if (!OrderTransition.PAY.canStartFrom(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        JSONObject jsonObject;
        try {
            jsonObject = stripePayUtil.createCheckoutSession(
                    ordersPaymentDTO.getOrderNumber(),
                    orders.getAmount(),
                    "Food Order #" + orders.getId()
            );
        } catch (StripeApiException e) {
            log.error("Could not create a Stripe checkout session for order {}", orders.getNumber(), e);
            throw new OrderBusinessException(MessageConstant.PAYMENT_PROVIDER_UNAVAILABLE);
        }


        OrderPaymentVO vo = new OrderPaymentVO();
        vo.setSessionId(jsonObject.getString("id"));
        vo.setCheckoutUrl(jsonObject.getString("url"));

        return vo;
    }

    /**
     * Payment notifications can arrive more than once (provider retries, a double click on the
     * simulated payment). Only the first one moves the order out of PENDING_PAYMENT and alerts the
     * merchant; repeated calls change nothing and send no WebSocket message.
     *
     * @param paymentIntentId Stripe's id for the payment, needed later to refund it;
     *                        null for a simulated payment
     */
    public boolean paySuccess(String outTradeNo, String paymentIntentId) {
        // Looked up by order number only: the Stripe webhook has no signed-in user.
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!OrderTransition.PAY.canStartFrom(ordersDB.getStatus())) {
            log.info("Ignoring payment notification for order {} in status {}", outTradeNo, ordersDB.getStatus());
            return false;
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(OrderTransition.PAY.getTargetStatus())
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .paymentIntentId(paymentIntentId)
                .build();
        if (orderMapper.updateStatus(orders, ordersDB.getStatus()) == 0) {
            log.info("Order {} was already updated by a concurrent payment notification", outTradeNo);
            return false;
        }


        Map map = new HashMap();
        map.put("type",1);
        map.put("orderId",ordersDB.getId());
        map.put("content","Order number: " + outTradeNo);

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
        return true;
    }

    /** Development checkout: marks one of the signed-in customer's own orders as paid. */
    public void simulatePayment(String orderNumber) {
        if (orderMapper.getByNumberAndUserId(orderNumber, BaseContext.getCurrentId()) == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        paySuccess(orderNumber, null);
    }

    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {

        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);


        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList();


        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                Long orderId = orders.getId();


                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }

    /** Merchant view: any order. */
    public OrderVO details(Long id) {
        return toOrderVO(getOrderOrThrow(id));
    }

    /** Customer view: only the signed-in customer's own orders. */
    public OrderVO detailsForCurrentUser(Long id) {
        return toOrderVO(getOwnOrderOrThrow(id));
    }

    private OrderVO toOrderVO(Orders orders) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    @Transactional
    public void userCancelById(Long id) throws Exception {
        Orders ordersDB = getOwnOrderOrThrow(id);
        boolean paid = Orders.PAID.equals(ordersDB.getPayStatus());

        Orders orders = new Orders();
        orders.setCancelReason("Canceled by customer");
        orders.setCancelTime(LocalDateTime.now());
        if (paid) {
            orders.setPayStatus(Orders.REFUND);
        }
        changeStatus(ordersDB, OrderTransition.USER_CANCEL, orders);

        if (paid) {
            refund(ordersDB);
        }
    }

    public void repetition(Long id) {

        Long userId = BaseContext.getCurrentId();
        getOwnOrderOrThrow(id);

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);


        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();


            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());


        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);


        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {

        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)) {
            for (Orders orders : ordersList) {

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);


                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    private String getOrderDishesStr(Orders orders) {

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());


        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());


        return String.join("", orderDishList);
    }

    public OrderStatisticsVO statistics() {

        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);


        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders ordersDB = getOrderOrThrow(ordersConfirmDTO.getId());
        changeStatus(ordersDB, OrderTransition.CONFIRM, new Orders());
    }

    @Transactional
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        Orders ordersDB = getOrderOrThrow(ordersRejectionDTO.getId());
        boolean paid = Orders.PAID.equals(ordersDB.getPayStatus());

        Orders orders = new Orders();
        orders.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orders.setCancelTime(LocalDateTime.now());
        if (paid) {
            orders.setPayStatus(Orders.REFUND);
        }
        changeStatus(ordersDB, OrderTransition.REJECT, orders);

        if (paid) {
            refund(ordersDB);
        }
    }

    @Transactional
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        Orders ordersDB = getOrderOrThrow(ordersCancelDTO.getId());
        boolean paid = Orders.PAID.equals(ordersDB.getPayStatus());

        Orders orders = new Orders();
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());
        if (paid) {
            orders.setPayStatus(Orders.REFUND);
        }
        changeStatus(ordersDB, OrderTransition.MERCHANT_CANCEL, orders);

        if (paid) {
            refund(ordersDB);
        }
    }

    public void delivery(Long id) {
        Orders ordersDB = getOrderOrThrow(id);
        changeStatus(ordersDB, OrderTransition.DELIVER, new Orders());
    }

    public void complete(Long id) {
        Orders ordersDB = getOrderOrThrow(id);

        Orders orders = new Orders();
        orders.setDeliveryTime(LocalDateTime.now());
        changeStatus(ordersDB, OrderTransition.COMPLETE, orders);
    }

    private Orders getOrderOrThrow(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return ordersDB;
    }

    /**
     * Like getOrderOrThrow, but another customer's order is reported as missing,
     * so a customer cannot read or act on it, or even learn that the id exists.
     */
    private Orders getOwnOrderOrThrow(Long id) {
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null || !ordersDB.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return ordersDB;
    }

    /**
     * Moves the order to the transition's target status with
     * UPDATE ... WHERE id = ? AND status = <status we just read>.
     * If another request changed the order in between, no row matches and the change is refused.
     */
    private void changeStatus(Orders ordersDB, OrderTransition transition, Orders changes) {
        if (!transition.canStartFrom(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        changes.setId(ordersDB.getId());
        changes.setStatus(transition.getTargetStatus());
        if (orderMapper.updateStatus(changes, ordersDB.getStatus()) == 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_CHANGED);
        }
    }

    /**
     * Called only after this request has won the status change, inside the same transaction.
     * It only queues the refund (the outbox); RefundWorker sends it to Stripe after the commit.
     * A slow or failing Stripe therefore cannot hold the order row locked or undo the cancellation,
     * and a cancellation that rolls back leaves no refund behind.
     */
    private void refund(Orders ordersDB) {
        if (ordersDB.getPaymentIntentId() == null) {
            log.info("Order {} was paid through the simulated checkout; there is nothing to refund at Stripe",
                    ordersDB.getNumber());
            return;
        }
        refundService.enqueue(ordersDB.getId(), ordersDB.getNumber(), ordersDB.getPaymentIntentId(), ordersDB.getAmount());
    }

    public void reminder(Long id) {

        Orders ordersDB = getOwnOrderOrThrow(id);

        Map map = new HashMap();
        map.put("type",2);
        map.put("orderId",id);
        map.put("content","Order number: " + ordersDB.getNumber());


        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }
}
