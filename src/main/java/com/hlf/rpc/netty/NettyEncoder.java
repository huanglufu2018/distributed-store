package com.hlf.rpc.netty;

import com.hlf.rpc.protocol.RemotingCommand;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.ByteBuffer;

/**
 * 自定义编码器
 */
public class NettyEncoder extends MessageToByteEncoder<RemotingCommand> {

    /**
     * 编码
     * @param ctx
     * @param remotingCommand
     * @param out
     * @throws Exception
     */
    protected void encode(ChannelHandlerContext ctx, RemotingCommand remotingCommand, ByteBuf out) throws Exception {
        try {
            ByteBuffer header = remotingCommand.encodeHeader();
            out.writeBytes(header);

            byte[] body = remotingCommand.getBody();
            if (body != null) {
                out.writeBytes(body);
            }
        } catch (Exception e) {
            ctx.channel().close();
        }
    }
}
