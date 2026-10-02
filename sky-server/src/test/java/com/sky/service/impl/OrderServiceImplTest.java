package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderTransition;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.utils.StripePayUtil;
import com.sky.vo.OrderSubmitVO;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
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

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void signIn() {
        BaseContext.setCurrentId(USER_ID);
    }

    @AfterEach
    void signOut() {
        BaseContext.removeCurrentId();
    }

    // ---------- placing an order ----------

    @Test
    void submitOrderSavesPendingOrderAndClearsCart() {
        when(addressBookMapper.getById(1L)).thenReturn(
                AddressBook.builder().id(1L).consignee("Alex").phone("4085550100").detail("1 Main St").build());
        ShoppingCart burger = ShoppingCart.builder().name("Burger").dishId(3L).number(2).amount(new BigDecimal("9.50")).build();
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.singletonList(burger));
        doAnswer(invocation -> {
            ((Orders) invocation.getArgument(0)).setId(ORDER_ID);
            return null;
        }).when(orderMapper).insert(any(Orders.class));

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);
        dto.setPayMethod(1);
        dto.setAmount(new BigDecimal("19.00"));
        dto.setPackAmount(1);
        dto.setTablewareNumber(1);
        dto.setTablewareStatus(1);
        dto.setDeliveryStatus(1);
        OrderSubmitVO result = orderService.submitOrder(dto);

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
    void submitOrderRejectsEmptyCart() {
        when(addressBookMapper.getById(1L)).thenReturn(AddressBook.builder().id(1L).build());
        when(shoppingCartMapper.list(any(ShoppingCart.class))).thenReturn(Collections.emptyList());

        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);

        ShoppingCartBusinessException e = assertThrows(ShoppingCartBusinessException.class,
                () -> orderService.submitOrder(dto));
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
        inOrder.verify(stripePayUtil).refund(ORDER_NUMBER, AMOUNT);
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

    // ---------- payment callbacks ----------

    @Test
    void paymentMarksOrderPaidAndAlertsMerchant() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(order(Orders.PENDING_PAYMENT));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);

        orderService.paySuccess(ORDER_NUMBER);

        ArgumentCaptor<Orders> update = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).updateStatus(update.capture(), eq(Orders.PENDING_PAYMENT));
        assertEquals(Orders.TO_BE_CONFIRMED, update.getValue().getStatus());
        assertEquals(Orders.PAID, update.getValue().getPayStatus());
        assertNotNull(update.getValue().getCheckoutTime());
        verify(webSocketServer).sendToAllClient(anyString());
    }

    @Test
    void repeatedPaymentCallbackUpdatesAndAlertsOnlyOnce() {
        // The second callback sees the order as the first one left it.
        when(orderMapper.getByNumber(ORDER_NUMBER))
                .thenReturn(order(Orders.PENDING_PAYMENT), order(Orders.TO_BE_CONFIRMED));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);

        orderService.paySuccess(ORDER_NUMBER);
        orderService.paySuccess(ORDER_NUMBER);

        verify(orderMapper, times(1)).updateStatus(any(Orders.class), any());
        verify(webSocketServer, times(1)).sendToAllClient(anyString());
    }

    @Test
    void concurrentPaymentCallbackThatLosesTheRaceSendsNoAlert() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(order(Orders.PENDING_PAYMENT));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(0);

        orderService.paySuccess(ORDER_NUMBER);

        verifyNoInteractions(webSocketServer);
    }

    @Test
    void paymentForUnknownOrderIsReported() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(null);

        OrderBusinessException e = assertThrows(OrderBusinessException.class,
                () -> orderService.paySuccess(ORDER_NUMBER));
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

    private static Orders order(Integer status) {
        return Orders.builder()
                .id(ORDER_ID)
                .number(ORDER_NUMBER)
                .userId(USER_ID)
                .status(status)
                .payStatus(Orders.PENDING_PAYMENT.equals(status) ? Orders.UN_PAID : Orders.PAID)
                .amount(AMOUNT)
                .orderTime(LocalDateTime.now())
                .build();
    }
}
