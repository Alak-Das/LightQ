package com.al.lightq.bench;

import com.al.lightq.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import java.util.Date;
import java.util.UUID;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Microbenchmarks for Message serialization/deserialization.
 * Compares JSON (Jackson) vs Smile+Afterburner (binary) used in Redis cache.
 *
 * Run:
 *  - From IDE: run the main().
 *  - From CLI: mvn -q -DskipTests=false -Dtest=com.al.lightq.bench.SerializationBenchTestPlaceholder test
 *    or run: java -jar target/benchmarks.jar (when assembled with JMH plugin) or use main().
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public class SerializationBench {

  @State(Scope.Thread)
  public static class BenchState {
    ObjectMapper jsonMapper;
    ObjectMapper smileMapper;
    Message sample;
    byte[] jsonBytes;
    byte[] smileBytes;

    @Setup(Level.Trial)
    public void setup() throws Exception {
      jsonMapper = new ObjectMapper();
      jsonMapper.findAndRegisterModules();
      jsonMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

      smileMapper = new ObjectMapper(new SmileFactory());
      smileMapper.findAndRegisterModules();
      smileMapper.registerModule(new AfterburnerModule());
      smileMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

      String id = UUID.randomUUID().toString();
      String consumerGroup = "bench-group";
      String content = "x".repeat(512); // 512B payload representative
      sample = new Message(id, consumerGroup, content);

      // precompute for deserialize benches
      jsonBytes = jsonMapper.writeValueAsBytes(sample);
      smileBytes = smileMapper.writeValueAsBytes(sample);
    }
  }

  @Benchmark
  public void serialize_json(BenchState s, Blackhole bh) throws Exception {
    bh.consume(s.jsonMapper.writeValueAsBytes(s.sample));
  }

  @Benchmark
  public void deserialize_json(BenchState s, Blackhole bh) throws Exception {
    bh.consume(s.jsonMapper.readValue(s.jsonBytes, Message.class));
  }

  @Benchmark
  public void serialize_smile_afterburner(BenchState s, Blackhole bh) throws Exception {
    bh.consume(s.smileMapper.writeValueAsBytes(s.sample));
  }

  @Benchmark
  public void deserialize_smile_afterburner(BenchState s, Blackhole bh) throws Exception {
    bh.consume(s.smileMapper.readValue(s.smileBytes, Message.class));
  }

  // Convenience entry-point to run JMH without additional plugins.
  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.runner.options.Options opt =
        new org.openjdk.jmh.runner.options.OptionsBuilder()
            .include(SerializationBench.class.getSimpleName())
            .detectJvmArgs()
            .build();
    new org.openjdk.jmh.runner.Runner(opt).run();
  }
}

// Placeholder test name to allow mvn test to compile annotations without executing real benches.
// Not a JUnit test class; only used to trigger annotation processing when needed.
class SerializationBenchTestPlaceholder {}
