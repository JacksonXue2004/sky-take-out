package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderTransition;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.exception.StripeApiException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.utils.StripePayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long ORDER_ID = 100L;
    private static final String ORDER_NUMBER = "1727850000000";
    private static final String IDEMPOTENCY_KEY = "3f6c2a9e-1b7d-4c55-9a0e-8d2f4b6c1e90";
    private static final String PAYMENT_INTENT = "pi_test_order_100";
    private static final BigDecimal AMOUNT = new BigDecimal("25.00");
    private static final Integer[] ALL_STATUSES = {
            Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED, Orders.CONFIRMED,
            Orders.DELIVERY_IN_PROGRESS, Orders.COMPLETED, Orders.CANCELLED};

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private AddressBookMapper addressBookMapper;
    @Mock
    private ShoppingCartMapper shoppingCartMapper;
    @Mock
    private StripePayUtil stripePayUtil;
    @Mock
    private WebSocketServer webSocketServer;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void signIn() {
        BaseContext.setCurrentId(USER_ID);
    }

    @BeforeEach
    void stripeAcceptsRefunds() {
        lenient().when(stripePayUtil.refund(any(), any(), any())).thenReturn(new JSONObject());
    }

    @BeforeEach
    void runTransactionsInline() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @AfterEach
    void signOut() {
        BaseContext.removeCurrentId();
    }

    // ---------- placing an order ----------

    @Test
    void submitOrderSavesPendingOrderAndClearsCart() {
        when(addressBookMapper.getById(1L)).thenReturn(
                AddressBook.builder().id(1L).userId(USER_ID).consignee("Alex").phone("4085550100").detail("1 Main St").build());
        ShoppingCart burger = ShoppingCart.builder().name("Burger").dishId(3L).number(2).amount(new BigDecimal("9.50")).build();
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.singletonList(burger));
        doAnswer(invocation -> {
            ((Orders) invocation.getArgument(0)).setId(ORDER_ID);
            return null;
        }).when(orderMapper).insert(any(Orders.class));

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);
        dto.setPayMethod(1);
        dto.setTablewareNumber(1);
        dto.setTablewareStatus(1);
        dto.setDeliveryStatus(1);
        OrderSubmitVO result = orderService.submitOrder(dto, null);

        ArgumentCaptor<Orders> saved = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(saved.capture());
        assertEquals(Orders.PENDING_PAYMENT, saved.getValue().getStatus());
        assertEquals(Orders.UN_PAID, saved.getValue().getPayStatus());
        assertEquals(USER_ID, saved.getValue().getUserId());
        assertEquals(ORDER_ID, result.getId());
        verify(orderDetailMapper).insertBatch(argThat(details ->
                details.size() == 1 && ORDER_ID.equals(details.get(0).getOrderId())));
        verify(shoppingCartMapper).deleteByUserId(USER_ID);
    }

    @Test
    void submitOrderComputesTheTotalFromTheCart() {
        when(addressBookMapper.getById(1L)).thenReturn(
                AddressBook.builder().id(1L).userId(USER_ID).consignee("Alex").phone("4085550100").detail("1 Main St").build());
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Arrays.asList(
                ShoppingCart.builder().name("Burger").dishId(3L).number(2).amount(new BigDecimal("9.50")).build(),
                ShoppingCart.builder().name("Fries").dishId(4L).number(1).amount(new BigDecimal("3.25")).build()));

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);
        OrderSubmitVO result = orderService.submitOrder(dto, null);

        ArgumentCaptor<Orders> saved = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(saved.capture());
        // 2 x 9.50 + 1 x 3.25
        assertEquals(0, new BigDecimal("22.25").compareTo(saved.getValue().getAmount()));
        assertEquals(0, new BigDecimal("22.25").compareTo(result.getOrderAmount()));
    }

    @Test
    void everyOrderGetsItsOwnRandomNumber() {
        when(addressBookMapper.getById(1L)).thenReturn(AddressBook.builder().id(1L).userId(USER_ID).build());
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.singletonList(
                ShoppingCart.builder().name("Burger").dishId(3L).number(1).amount(new BigDecimal("9.50")).build()));
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);

        orderService.submitOrder(dto, null);
        orderService.submitOrder(dto, null);

        ArgumentCaptor<Orders> saved = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper, times(2)).insert(saved.capture());
        String first = saved.getAllValues().get(0).getNumber();
        String second = saved.getAllValues().get(1).getNumber();
        assertTrue(first.matches("[0-9a-f]{32}"), first);
        assertNotEquals(first, second);
    }

    // ---------- idempotent order placement ----------

    @Test
    void aKeyThatWasAlreadyUsedReturnsTheFirstOrderWithoutPlacingAnother() {
        Orders first = order(Orders.PENDING_PAYMENT);
        when(orderMapper.getByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY)).thenReturn(first);

        OrderSubmitVO result = orderService.submitOrder(new OrdersSubmitDTO(), IDEMPOTENCY_KEY);

        assertEquals(ORDER_ID, result.getId());
        assertEquals(ORDER_NUMBER, result.getOrderNumber());
        verify(orderMapper, never()).insert(any());
        verifyNoInteractions(shoppingCartMapper, addressBookMapper);
    }

    @Test
    void aNewKeyIsStoredWithTheOrder() {
        when(addressBookMapper.getById(1L)).thenReturn(AddressBook.builder().id(1L).userId(USER_ID).build());
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.singletonList(
                ShoppingCart.builder().name("Burger").dishId(3L).number(1).amount(new BigDecimal("9.50")).build()));
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);

        orderService.submitOrder(dto, IDEMPOTENCY_KEY);

        verify(orderMapper).insert(argThat(o -> IDEMPOTENCY_KEY.equals(o.getIdempotencyKey())));
    }

    @Test
    void losingARaceOnTheSameKeyReturnsTheWinnersOrder() {
        // Nothing yet when this request starts; by the time it inserts, the other request has committed.
        when(orderMapper.getByUserIdAndIdempotencyKey(USER_ID, IDEMPOTENCY_KEY))
                .thenReturn(null, order(Orders.PENDING_PAYMENT));
        when(addressBookMapper.getById(1L)).thenReturn(AddressBook.builder().id(1L).userId(USER_ID).build());
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.singletonList(
                ShoppingCart.builder().name("Burger").dishId(3L).number(1).amount(new BigDecimal("9.50")).build()));
        doThrow(new DuplicateKeyException("uk_orders_user_idempotency_key")).when(orderMapper).insert(any(Orders.class));
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);

        OrderSubmitVO result = orderService.submitOrder(dto, IDEMPOTENCY_KEY);

        assertEquals(ORDER_ID, result.getId());
        verify(shoppingCartMapper, never()).deleteByUserId(any());
    }

    @Test
    void aMalformedKeyIsRefused() {
        OrderBusinessException e = assertThrows(OrderBusinessException.class,
                () -> orderService.submitOrder(new OrdersSubmitDTO(), "not a key!"));
        assertEquals(MessageConstant.IDEMPOTENCY_KEY_INVALID, e.getMessage());
        verifyNoInteractions(orderMapper, shoppingCartMapper);
    }

    @Test
    void submitOrderRejectsEmptyCart() {
        when(addressBookMapper.getById(1L)).thenReturn(AddressBook.builder().id(1L).userId(USER_ID).build());
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.emptyList());

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);

        ShoppingCartBusinessException e = assertThrows(ShoppingCartBusinessException.class,
                () -> orderService.submitOrder(dto, null));
        assertEquals(MessageConstant.SHOPPING_CART_IS_NULL, e.getMessage());
        verify(orderMapper, never()).insert(any());
    }

    // ---------- status transitions: every action against every status ----------

    @ParameterizedTest(name = "{0} from status {1}")
    @MethodSource("legalTransitions")
    void legalTransitionUsesConditionalUpdate(Action action, Integer fromStatus) throws Exception {
        when(orderMapper.getById(ORDER_ID)).thenReturn(order(fromStatus));
        when(orderMapper.updateStatus(any(Orders.class), eq(fromStatus))).thenReturn(1);

        action.run(orderService, ORDER_ID);

        ArgumentCaptor<Orders> update = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).updateStatus(update.capture(), eq(fromStatus));
        assertEquals(ORDER_ID, update.getValue().getId());
        assertEquals(action.transition.getTargetStatus(), update.getValue().getStatus());
    }

    @ParameterizedTest(name = "{0} from status {1}")
    @MethodSource("illegalTransitions")
    void illegalTransitionIsRejectedBeforeAnyUpdate(Action action, Integer fromStatus) {
        when(orderMapper.getById(ORDER_ID)).thenReturn(order(fromStatus));

        OrderBusinessException e = assertThrows(OrderBusinessException.class,
                () -> action.run(orderService, ORDER_ID));
        assertEquals(MessageConstant.ORDER_STATUS_ERROR, e.getMessage());
        verify(orderMapper, never()).updateStatus(any(), any());
        verifyNoInteractions(stripePayUtil);
    }

    @Test
    void losingAConcurrentUpdateReportsThatTheOrderChanged() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(order(Orders.TO_BE_CONFIRMED));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.TO_BE_CONFIRMED))).thenReturn(0);

        OrderBusinessException e = assertThrows(OrderBusinessException.class,
                () -> Action.CONFIRM.run(orderService, ORDER_ID));
        assertEquals(MessageConstant.ORDER_STATUS_CHANGED, e.getMessage());
    }

    @Test
    void rejectingAPaidOrderRefundsOnlyAfterTheStatusChange() throws Exception {
        when(orderMapper.getById(ORDER_ID)).thenReturn(order(Orders.TO_BE_CONFIRMED));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.TO_BE_CONFIRMED))).thenReturn(1);

        Action.REJECT.run(orderService, ORDER_ID);

        InOrder inOrder = inOrder(orderMapper, stripePayUtil);
        inOrder.verify(orderMapper).updateStatus(
                argThat(o -> Orders.REFUND.equals(o.getPayStatus())), eq(Orders.TO_BE_CONFIRMED));
        inOrder.verify(stripePayUtil).refund(PAYMENT_INTENT, AMOUNT, "refund-" + ORDER_NUMBER);
    }

    @Test
    void anOrderPaidThroughTheSimulatedCheckoutIsNotRefundedAtStripe() throws Exception {
        Orders simulated = order(Orders.TO_BE_CONFIRMED);
        simulated.setPaymentIntentId(null);
        when(orderMapper.getById(ORDER_ID)).thenReturn(simulated);
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.TO_BE_CONFIRMED))).thenReturn(1);

        Action.REJECT.run(orderService, ORDER_ID);

        verify(stripePayUtil, never()).refund(any(), any(), any());
    }

    @Test
    void noRefundWhenAnotherRequestChangedTheOrderFirst() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(order(Orders.TO_BE_CONFIRMED));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.TO_BE_CONFIRMED))).thenReturn(0);

        assertThrows(OrderBusinessException.class, () -> Action.REJECT.run(orderService, ORDER_ID));
        verifyNoInteractions(stripePayUtil);
    }

    @Test
    void missingOrderIsReportedAsNotFound() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(null);

        OrderBusinessException e = assertThrows(OrderBusinessException.class,
                () -> orderService.delivery(ORDER_ID));
        assertEquals(MessageConstant.ORDER_NOT_FOUND, e.getMessage());
    }

    @Test
    void customerCannotCancelAnotherCustomersOrder() {
        Orders someoneElses = order(Orders.PENDING_PAYMENT);
        someoneElses.setUserId(8L);
        when(orderMapper.getById(ORDER_ID)).thenReturn(someoneElses);

        OrderBusinessException e = assertThrows(OrderBusinessException.class,
                () -> orderService.userCancelById(ORDER_ID));
        assertEquals(MessageConstant.ORDER_NOT_FOUND, e.getMessage());
        verify(orderMapper, never()).updateStatus(any(), any());
    }

    // ---------- customers only reach their own orders and addresses ----------

    @Test
    void submitOrderRejectsAnotherCustomersAddress() {
        when(addressBookMapper.getById(1L)).thenReturn(AddressBook.builder().id(1L).userId(8L).build());

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);

        AddressBookBusinessException e = assertThrows(AddressBookBusinessException.class,
                () -> orderService.submitOrder(dto, null));
        assertEquals(MessageConstant.ADDRESS_BOOK_IS_NULL, e.getMessage());
        verifyNoInteractions(shoppingCartMapper);
        verify(orderMapper, never()).insert(any());
    }

    @Test
    void customerCanReadTheirOwnOrder() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(order(Orders.TO_BE_CONFIRMED));

        OrderVO vo = orderService.detailsForCurrentUser(ORDER_ID);

        assertEquals(ORDER_ID, vo.getId());
        verify(orderDetailMapper).getByOrderId(ORDER_ID);
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(orderOf(8L, Orders.TO_BE_CONFIRMED));

        OrderBusinessException e = assertThrows(OrderBusinessException.class,
                () -> orderService.detailsForCurrentUser(ORDER_ID));
        assertEquals(MessageConstant.ORDER_NOT_FOUND, e.getMessage());
        verifyNoInteractions(orderDetailMapper);
    }

    @Test
    void merchantDetailsReportAMissingOrderInsteadOfFailing() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(null);

        OrderBusinessException e = assertThrows(OrderBusinessException.class,
                () -> orderService.details(ORDER_ID));
        assertEquals(MessageConstant.ORDER_NOT_FOUND, e.getMessage());
    }

    @Test
    void customerCannotReorderAnotherCustomersOrder() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(orderOf(8L, Orders.COMPLETED));

        assertThrows(OrderBusinessException.class, () -> orderService.repetition(ORDER_ID));
        verifyNoInteractions(orderDetailMapper, shoppingCartMapper);
    }

    @Test
    void customerCannotRemindTheMerchantAboutAnotherCustomersOrder() {
        when(orderMapper.getById(ORDER_ID)).thenReturn(orderOf(8L, Orders.CONFIRMED));

        assertThrows(OrderBusinessException.class, () -> orderService.reminder(ORDER_ID));
        verifyNoInteractions(webSocketServer);
    }

    @Test
    void customerCannotStartPaymentForAnotherCustomersOrder() {
        // The lookup includes the signed-in customer's id, so someone else's order is not found.
        when(orderMapper.getByNumberAndUserId(ORDER_NUMBER, USER_ID)).thenReturn(null);
        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber(ORDER_NUMBER);

        OrderBusinessException e = assertThrows(OrderBusinessException.class, () -> orderService.payment(dto));
        assertEquals(MessageConstant.ORDER_NOT_FOUND, e.getMessage());
        verifyNoInteractions(stripePayUtil);
    }

    @Test
    void simulatedPaymentOnlyPaysTheCustomersOwnOrder() {
        when(orderMapper.getByNumberAndUserId(ORDER_NUMBER, USER_ID)).thenReturn(null);

        assertThrows(OrderBusinessException.class, () -> orderService.simulatePayment(ORDER_NUMBER));
        verify(orderMapper, never()).updateStatus(any(), any());
        verifyNoInteractions(webSocketServer);
    }

    // ---------- payment callbacks ----------

    @Test
    void paymentMarksOrderPaidAndAlertsMerchant() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(order(Orders.PENDING_PAYMENT));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);

        orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT);

        ArgumentCaptor<Orders> update = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).updateStatus(update.capture(), eq(Orders.PENDING_PAYMENT));
        assertEquals(Orders.TO_BE_CONFIRMED, update.getValue().getStatus());
        assertEquals(Orders.PAID, update.getValue().getPayStatus());
        assertNotNull(update.getValue().getCheckoutTime());
        verify(webSocketServer).sendToAllClient(anyString());
    }

    @Test
    void paymentRecordsTheStripePaymentIntentForLaterRefunds() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(order(Orders.PENDING_PAYMENT));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);

        orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT);

        verify(orderMapper).updateStatus(argThat(o -> PAYMENT_INTENT.equals(o.getPaymentIntentId())),
                eq(Orders.PENDING_PAYMENT));
    }

    @Test
    void checkoutReturnsTheStripeSession() throws Exception {
        when(orderMapper.getByNumberAndUserId(ORDER_NUMBER, USER_ID)).thenReturn(order(Orders.PENDING_PAYMENT));
        JSONObject session = new JSONObject();
        session.put("id", "cs_test_1");
        session.put("url", "https://checkout.stripe.com/c/pay/cs_test_1");
        when(stripePayUtil.createCheckoutSession(eq(ORDER_NUMBER), eq(AMOUNT), anyString())).thenReturn(session);

        OrderPaymentVO vo = orderService.payment(paymentRequest());

        assertEquals("cs_test_1", vo.getSessionId());
        assertEquals("https://checkout.stripe.com/c/pay/cs_test_1", vo.getCheckoutUrl());
    }

    @Test
    void anOrderThatIsNoLongerPendingCannotBePaid() {
        Orders cancelled = order(Orders.CANCELLED);
        cancelled.setPayStatus(Orders.UN_PAID);
        when(orderMapper.getByNumberAndUserId(ORDER_NUMBER, USER_ID)).thenReturn(cancelled);

        OrderBusinessException e = assertThrows(OrderBusinessException.class, () -> orderService.payment(paymentRequest()));
        assertEquals(MessageConstant.ORDER_STATUS_ERROR, e.getMessage());
        verifyNoInteractions(stripePayUtil);
    }

    @Test
    void aStripeOutageDuringCheckoutIsReportedToTheCustomer() {
        when(orderMapper.getByNumberAndUserId(ORDER_NUMBER, USER_ID)).thenReturn(order(Orders.PENDING_PAYMENT));
        when(stripePayUtil.createCheckoutSession(anyString(), any(), anyString()))
                .thenThrow(new StripeApiException(503, "Stripe returned 503", true));

        OrderBusinessException e = assertThrows(OrderBusinessException.class, () -> orderService.payment(paymentRequest()));
        assertEquals(MessageConstant.PAYMENT_PROVIDER_UNAVAILABLE, e.getMessage());
    }

    @Test
    void repeatedPaymentCallbackUpdatesAndAlertsOnlyOnce() {
        // The second callback sees the order as the first one left it.
        when(orderMapper.getByNumber(ORDER_NUMBER))
                .thenReturn(order(Orders.PENDING_PAYMENT), order(Orders.TO_BE_CONFIRMED));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);

        orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT);
        orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT);

        verify(orderMapper, times(1)).updateStatus(any(Orders.class), any());
        verify(webSocketServer, times(1)).sendToAllClient(anyString());
    }

    @Test
    void concurrentPaymentCallbackThatLosesTheRaceSendsNoAlert() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(order(Orders.PENDING_PAYMENT));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(0);

        orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT);

        verifyNoInteractions(webSocketServer);
    }

    @Test
    void paymentForUnknownOrderIsReported() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(null);

        OrderBusinessException e = assertThrows(OrderBusinessException.class,
                () -> orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT));
        assertEquals(MessageConstant.ORDER_NOT_FOUND, e.getMessage());
        verifyNoInteractions(webSocketServer);
    }

    // ---------- helpers ----------

    /** The service operations that change an order's status, each with the transition it uses. */
    enum Action {
        CONFIRM(OrderTransition.CONFIRM),
        REJECT(OrderTransition.REJECT),
        DELIVER(OrderTransition.DELIVER),
        COMPLETE(OrderTransition.COMPLETE),
        MERCHANT_CANCEL(OrderTransition.MERCHANT_CANCEL),
        USER_CANCEL(OrderTransition.USER_CANCEL);

        final OrderTransition transition;

        Action(OrderTransition transition) {
            this.transition = transition;
        }

        void run(OrderServiceImpl service, Long orderId) throws Exception {
            switch (this) {
                case CONFIRM:
                    OrdersConfirmDTO confirm = new OrdersConfirmDTO();
                    confirm.setId(orderId);
                    service.confirm(confirm);
                    break;
                case REJECT:
                    OrdersRejectionDTO reject = new OrdersRejectionDTO();
                    reject.setId(orderId);
                    reject.setRejectionReason("Out of stock");
                    service.rejection(reject);
                    break;
                case DELIVER:
                    service.delivery(orderId);
                    break;
                case COMPLETE:
                    service.complete(orderId);
                    break;
                case MERCHANT_CANCEL:
                    OrdersCancelDTO cancel = new OrdersCancelDTO();
                    cancel.setId(orderId);
                    cancel.setCancelReason("Restaurant is closing early");
                    service.cancel(cancel);
                    break;
                case USER_CANCEL:
                    service.userCancelById(orderId);
                    break;
                default:
                    throw new IllegalStateException("Unknown action " + this);
            }
        }
    }

    static Stream<Arguments> legalTransitions() {
        return combinations(true);
    }

    static Stream<Arguments> illegalTransitions() {
        return combinations(false);
    }

    private static Stream<Arguments> combinations(boolean legal) {
        List<Arguments> result = new ArrayList<>();
        for (Action action : Action.values()) {
            for (Integer status : ALL_STATUSES) {
                if (action.transition.canStartFrom(status) == legal) {
                    result.add(Arguments.of(action, status));
                }
            }
        }
        return result.stream();
    }

    private static OrdersPaymentDTO paymentRequest() {
        OrdersPaymentDTO dto = new OrdersPaymentDTO();
        dto.setOrderNumber(ORDER_NUMBER);
        return dto;
    }

    private static Orders order(Integer status) {
        return orderOf(USER_ID, status);
    }

    private static Orders orderOf(Long userId, Integer status) {
        return Orders.builder()
                .id(ORDER_ID)
                .number(ORDER_NUMBER)
                .userId(userId)
                .status(status)
                .payStatus(Orders.PENDING_PAYMENT.equals(status) ? Orders.UN_PAID : Orders.PAID)
                .paymentIntentId(Orders.PENDING_PAYMENT.equals(status) ? null : PAYMENT_INTENT)
                .amount(AMOUNT)
                .orderTime(LocalDateTime.now())
                .build();
    }
}
