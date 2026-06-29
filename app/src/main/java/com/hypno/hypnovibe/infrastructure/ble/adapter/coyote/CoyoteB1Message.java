package com.hypno.hypnovibe.infrastructure.ble.adapter.coyote;

/**
 * 郊狼 V3 B1 强度反馈消息。
 * <p>
 * 设备通过 Notify 特性返回，通知当前实际强度。
 * package-private，仅郊狼子包内部使用。
 */
final class CoyoteB1Message {

    /** 序列号（与触发此反馈的B0一致，或0表示由物理滚轮/其他原因触发） */
    final int seqNo;

    /** A通道当前实际强度 0-200 */
    final int strengthA;

    /** B通道当前实际强度 0-200 */
    final int strengthB;

    CoyoteB1Message(int seqNo, int strengthA, int strengthB) {
        this.seqNo = seqNo;
        this.strengthA = strengthA;
        this.strengthB = strengthB;
    }

    @Override
    public String toString() {
        return "B1Message{seq=" + seqNo + ", A=" + strengthA + ", B=" + strengthB + "}";
    }
}
