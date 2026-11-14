package ar.edu.tudai.ticketya.servicio_entradas.integration;
public interface OrderClient {
    OrderSnapshot getOrder(String orderId);
    default OrderSnapshot getOrderForOwnership(String orderId){return getOrder(orderId);}
}
