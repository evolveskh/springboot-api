ALTER TABLE transactions
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';

CREATE INDEX idx_transactions_status ON transactions(status);
