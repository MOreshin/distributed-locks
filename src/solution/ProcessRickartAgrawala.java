package solution;

import internal.Environment;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Mikhail Oreshin
 */
public class ProcessRickartAgrawala implements MutexProcess {
    private final Environment env;
    private int time = 0;
    private int mySendTime = -1;
    private int oks = 0;
    private Queue<Integer> toAccept = new LinkedList<>();

    public ProcessRickartAgrawala(Environment env) {
        this.env = env;
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        if (message instanceof MyMessage msg) {
            time = Math.max(time, msg.time + 1);
            if (msg.type == REQ) {
                if (mySendTime == -1 || mySendTime > msg.time || (mySendTime == msg.time && env.getProcessId() > sourcePid)) {
                    env.send(sourcePid, new MyMessage(time++, OK));
                } else {
                    toAccept.add(sourcePid);
                }
                return;
            }
            if (msg.type == OK) {
                ++oks;
                if (oks == env.getNumberOfProcesses() - 1) {
                    env.lock();
                }
                return;
            }
        }
        throw new UnsupportedOperationException("Unexpected message: " + message);
    }

    @Override
    public void onLockRequest() {
        mySendTime = time;
        oks = 0;
        for (int i = 1; i <= env.getNumberOfProcesses(); ++i) {
            if (i == env.getProcessId()) {
                continue;
            }
            env.send(i, new MyMessage(time, REQ));
        }
        ++time;
        if (env.getNumberOfProcesses() == 1) {
            env.lock();
        }
    }

    @Override
    public void onUnlockRequest() {
        mySendTime = -1;
        env.unlock();
        while (!toAccept.isEmpty()) {
            int x = toAccept.poll();
            env.send(x, new MyMessage(time++, OK));
        }
    }

    private record MyMessage(int time, byte type) implements java.io.Serializable {}
    private static final byte REQ = 1;
    private static final byte OK = 2;
}
