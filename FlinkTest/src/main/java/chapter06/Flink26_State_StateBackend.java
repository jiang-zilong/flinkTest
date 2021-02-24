package chapter06;

import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.contrib.streaming.state.RocksDBStateBackend;
import org.apache.flink.runtime.state.StateBackend;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.runtime.state.memory.MemoryStateBackend;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.BroadcastConnectedStream;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;

/**
 * TODO
 *
 * @author cjp
 * @version 1.0
 * @date 2020/12/1 8:54
 */
public class Flink26_State_StateBackend {
    public static void main(String[] args) throws Exception {
        // 1.创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        // TODO 状态后端的使用
        // 1. MemoryStateBackend的使用
        StateBackend memoryStateBackend = new MemoryStateBackend();
        env.setStateBackend(memoryStateBackend);
        // 2. FsStateBackend的使用
        StateBackend fsStateBackend = new FsStateBackend("hdfs://host:port/xxx/xxx/xxx");
        env.setStateBackend(fsStateBackend);
        // 3. RocksDBStateBackend的使用 （需要 导入 依赖）
        StateBackend rocksDBStateBackend = new RocksDBStateBackend("hdfs://{fs.defaultFS}/xxx/xxx/xxx");
        env.setStateBackend(rocksDBStateBackend);

        // TODO 要使用状态后端，一定要开启checkpoint
        env.enableCheckpointing(111111L);

        // 一条流A
        DataStreamSource<String> inputDS = env.socketTextStream("localhost", 9999);

        // 另一条流 B
        DataStreamSource<String> controlDS = env.socketTextStream("localhost", 8888);

        // TODO 应用场景（1.5版本才有的）
        // 1.动态配置更新
        // 2.类似开关的功能， 切换处理逻辑

        // TODO 限制
        // 1. 要广播出去的流B，最好是 数据量小、 更新不频繁


        // TODO 1.将 其中一条流 B 广播出去
        MapStateDescriptor<String, String> broadcastMapStateDesc = new MapStateDescriptor<>("broadcast-state", String.class, String.class);
        BroadcastStream<String> controlBS = controlDS.broadcast(broadcastMapStateDesc);

        // TODO 2.连接 流 A 和 广播B
        BroadcastConnectedStream<String, String> inputControlBCS = inputDS.connect(controlBS);

        // TODO 3.使用 Process
        inputControlBCS
                .process(
                        new BroadcastProcessFunction<String, String, String>() {
                            /**
                             * 处理 流 A的数据
                             * @param value
                             * @param ctx
                             * @param out
                             * @throws Exception
                             */
                            @Override
                            public void processElement(String value, ReadOnlyContext ctx, Collector<String> out) throws Exception {
                                // 主流 A 获取 广播状态，但是 只读的，不能修改，要在 流 B去更新
                                ReadOnlyBroadcastState<String, String> broadcastState = ctx.getBroadcastState(broadcastMapStateDesc);
                                if ("1".equals(broadcastState.get("switch"))) {
                                    out.collect("打开....");
                                } else {
                                    out.collect("不打开...");
                                }
                            }

                            /**
                             * 处理 广播流B 的数据
                             * @param value
                             * @param ctx
                             * @param out
                             * @throws Exception
                             */
                            @Override
                            public void processBroadcastElement(String value, Context ctx, Collector<String> out) throws Exception {
                                BroadcastState<String, String> broadcastState = ctx.getBroadcastState(broadcastMapStateDesc);
                                // 把 数据 写入 广播状态
                                broadcastState.put("switch", value);
                            }
                        }
                )
                .print();


        env.execute();
    }
}
