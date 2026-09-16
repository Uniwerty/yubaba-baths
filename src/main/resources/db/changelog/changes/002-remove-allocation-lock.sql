ALTER TABLE accounts ADD COLUMN active_order_id bigint;

UPDATE accounts
SET active_order_id = assignments.order_id
FROM order_attendants assignments
JOIN bath_orders orders ON orders.id = assignments.order_id
WHERE accounts.id = assignments.account_id
  AND orders.status IN ('IN_SERVICE', 'AWAITING_PAYMENT');

DROP TABLE allocation_lock;
