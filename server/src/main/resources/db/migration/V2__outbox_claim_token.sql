-- 2. outbox_event 认领所有权令牌（写入方：CM，随 01 §5 / 02 §5 发件箱租约修复）
--     claimNext 每次认领生成唯一 claim_token；成功/失败记账必须匹配本次认领令牌，
--     租约过期被他人重认领后，旧认领者的 markDone/recordFailure 因令牌不匹配而影响 0 行，
--     防止僵尸消费者篡改新认领者的状态与重试预算。
ALTER TABLE outbox_event ADD COLUMN claim_token uuid NULL;
