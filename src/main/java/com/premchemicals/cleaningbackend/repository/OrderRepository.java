package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.Order;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.OrderStatus;
import com.premchemicals.cleaningbackend.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;

import com.premchemicals.cleaningbackend.model.Product;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ✅ Get orders of specific user WITH items + product
    @EntityGraph(attributePaths = {"orderItems", "orderItems.product"})
    List<Order> findByUser(User user);

    @EntityGraph(attributePaths = {"orderItems", "orderItems.product"})
    Optional<Order> findWithItemsById(Long id);

    // ✅ Get all orders WITH items + product (for admin)
    @Override
    @EntityGraph(attributePaths = {"orderItems", "orderItems.product"})
    List<Order> findAll();

    // ✅ Required for Razorpay Webhook
    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    // =========================================================
    // 🔥 ADMIN MANAGEMENT QUERIES
    // =========================================================

    // Get orders by status
    @EntityGraph(attributePaths = {"orderItems", "orderItems.product"})
    List<Order> findByOrderStatus(OrderStatus status);

    // Get orders between dates
    @EntityGraph(attributePaths = {"orderItems", "orderItems.product"})
    List<Order> findByOrderDateBetween(LocalDateTime start, LocalDateTime end);

    // Revenue between dates
    @Query("""
           SELECT COALESCE(SUM(o.totalAmount), 0)
           FROM Order o
           WHERE o.paymentStatus = :paymentStatus
           AND o.orderDate BETWEEN :start AND :end
           """)
    Double calculateRevenueBetweenDates(
            PaymentStatus paymentStatus,
            LocalDateTime start,
            LocalDateTime end
    );

    // =========================================================
// 🔥 TOP SELLING PRODUCTS ANALYTICS
// =========================================================

    @Query("""
       SELECT 
           oi.product.name,
           SUM(oi.quantity),
           SUM(oi.quantity * oi.price)
       FROM Order o
       JOIN o.orderItems oi
       WHERE o.paymentStatus = 'COMPLETED'
       GROUP BY oi.product.name
       ORDER BY SUM(oi.quantity) DESC
       """)
    List<Object[]> findTopSellingProducts();

    // =========================================================
// 🔥 WORST SELLING PRODUCTS ANALYTICS
// =========================================================

    @Query("""
       SELECT 
           oi.product.name,
           SUM(oi.quantity),
           SUM(oi.quantity * oi.price)
       FROM Order o
       JOIN o.orderItems oi
       WHERE o.paymentStatus = 'COMPLETED'
       GROUP BY oi.product.name
       ORDER BY SUM(oi.quantity) ASC
       """)
    List<Object[]> findWorstSellingProducts();

    // =========================================================
// 🔥 DEAD STOCK (NO SALES IN LAST 30 DAYS)
// =========================================================

    @Query("""
       SELECT p
       FROM Product p
       WHERE p.active = true
       AND p.id NOT IN (
            SELECT oi.product.id
            FROM Order o
            JOIN o.orderItems oi
            WHERE o.paymentStatus = 'COMPLETED'
            AND o.orderDate >= :date
       )
       """)
    List<Product> findDeadStockProducts(LocalDateTime date);

    // =========================================================
// 🔥 SKU MARGIN RANKING
// =========================================================

    // =========================================================
// 🔥 SKU MARGIN RANKING
// =========================================================
    @Query("""
   SELECT 
       oi.product.name,
       oi.product.price,
       oi.product.costPrice,
       SUM((oi.product.price - oi.product.costPrice) * oi.quantity)
   FROM Order o
   JOIN o.orderItems oi
   WHERE o.paymentStatus = 'COMPLETED'
   GROUP BY oi.product.name, oi.product.price, oi.product.costPrice
   ORDER BY SUM((oi.product.price - oi.product.costPrice) * oi.quantity) DESC
   """)
    List<Object[]> getMarginRanking();

    @Query("""
       SELECT 
           oi.product.name,
           SUM(oi.quantity)
       FROM Order o
       JOIN o.orderItems oi
       WHERE o.paymentStatus = 'COMPLETED'
       AND o.orderDate >= :startDate
       GROUP BY oi.product.name
       """)
    List<Object[]> getLast30DaysSales(LocalDateTime startDate);

    // =========================================================
// 🔥 CUSTOMER REPEAT ANALYTICS
// =========================================================
    @Query("""
       SELECT o.user.id, COUNT(o.id)
       FROM Order o
       WHERE o.paymentStatus = 'COMPLETED'
       GROUP BY o.user.id
       """)
    List<Object[]> getCustomerOrderCounts();

    // =========================================================
// 🔥 DELIVERY PERFORMANCE
// =========================================================
    @Query("""
       SELECT AVG(
           TIMESTAMPDIFF(MINUTE, o.orderDate, o.deliveredAt)
       )
       FROM Order o
       WHERE o.orderStatus = 'DELIVERED'
       """)
    Double getAverageDeliveryTimeInMinutes();


    @Query("""
SELECT COUNT(o) > 0
FROM Order o
JOIN o.orderItems oi
WHERE o.user.id = :userId
AND oi.product.id = :productId
AND o.orderStatus = 'DELIVERED'
""")

    boolean hasPurchasedProduct(

            Long userId,

            Long productId
    );


    // =========================================================
    // 🔥 DASHBOARD COUNT QUERIES
    // =========================================================

    // 1️⃣ Count orders between dates
    long countByOrderDateBetween(LocalDateTime start, LocalDateTime end);

    @Query("""
           SELECT COUNT(o)
           FROM Order o
           WHERE o.orderDate BETWEEN :start AND :end
           AND (o.paymentMethod != 'ONLINE' OR o.paymentStatus = 'COMPLETED')
           """)
    long countConfirmedOrdersBetween(LocalDateTime start, LocalDateTime end);

    // 2️⃣ Count orders by status
    long countByOrderStatus(OrderStatus status);

    @Query("""
           SELECT COUNT(o)
           FROM Order o
           WHERE o.orderStatus = :status
           AND (o.paymentMethod != 'ONLINE' OR o.paymentStatus = 'COMPLETED')
           """)
    long countConfirmedOrdersByOrderStatus(OrderStatus status);

    // 3️⃣ Count completed payments
    long countByPaymentStatus(PaymentStatus status);
}