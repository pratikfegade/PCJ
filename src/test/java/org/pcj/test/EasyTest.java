/*
 * Test app
 */
package org.pcj.test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.pcj.NodesDescription;
import org.pcj.PCJ;
import org.pcj.StartPoint;
import org.pcj.Storage;

/**
 *
 * @author faramir
 */
public class EasyTest extends Storage implements StartPoint {

    public static void main(String[] args) throws InterruptedException {
        Level level = Level.INFO;
//        Level level = Level.FINEST;
        Logger logger = Logger.getLogger("");
        Arrays.stream(logger.getHandlers()).forEach(handler -> handler.setLevel(level));
        logger.setLevel(level);

        NodesDescription nodesDescription = new NodesDescription(new String[]{
            "localhost:8091", //            "localhost:8091",
        //            "localhost:8091",
        //            "localhost:8002",
        //            "localhost:8003",
        //            "localhost:8004",
        //            "localhost:8003",
        //            "localhost:8005",
        //            "localhost:8006",
        //            "localhost:8007",
        //            "localhost:8008",
        //            "localhost:8008",
        //            "localhost:8008",
        //            "localhost:8008",
        //            "localhost:8009", // run.jvmargs=-Xmx64m
        //            "localhost:8010",
        //            "localhost:8011",
        //            "localhost:8012",
        //            "localhost:8013",
        //            "localhost:8014",
        //            "localhost:8015",
        //            "localhost:8016",
        //            "localhost:8017",
        //            "localhost:8018",
        //            "localhost:8019",
        });

//        PCJ.start(EasyTest.class, EasyTest.class,
        PCJ.deploy(EasyTest.class, EasyTest.class, nodesDescription);
    }

    @Override
    public void main() throws Throwable {
//        Level level = Level.FINEST;
//        Logger logger = Logger.getLogger("");
//        Arrays.stream(logger.getHandlers()).forEach(handler -> handler.setLevel(level));
//        logger.setLevel(level);

        for (int i = 0; i < PCJ.threadCount(); ++i) {
            if (PCJ.myId() == i) {
                System.out.println("Starting as " + PCJ.myId());
            }
            PCJ.barrier();
        }

        PCJ.createShared("a", double.class);
        PCJ.createShared("b", double.class);
        PCJ.createShared("c", Double.class);
        long start=System.nanoTime();
        new Thread(() -> {
            LockSupport.parkNanos(4_999_999_999L);
            PCJ.putLocal("a", (System.nanoTime()-start)/1e9);
        }).start();
        PCJ.waitFor("a", 1, 5, TimeUnit.SECONDS);
//        PCJ.putLocal("a", new Long(2));
        PCJ.putLocal("b", 'b');
        PCJ.putLocal("c", 2);

        System.out.println("a=" + PCJ.getLocal("a") + " " + PCJ.getLocal("a").getClass());
        System.out.println("b=" + PCJ.getLocal("b") + " " + PCJ.getLocal("b").getClass());
        System.out.println("c=" + PCJ.getLocal("c"));
        PCJ.putLocal("c", null);
        System.out.println("c=" + PCJ.getLocal("c"));
    }
}
