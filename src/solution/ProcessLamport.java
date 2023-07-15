package solution;

import internal.Environment;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Mikhail Oreshin
 */
public class ProcessLamport implements MutexProcess {
    private final Environment env;
    private int time = 0;
    private final Queue<Request> requests = new PriorityQueue<>();
    int oks = -1;//value for "not waiting"

    public ProcessLamport(Environment env) {
        this.env = env;
    }

    private void send(byte type, int dest) {
        //System.out.println("sending:" + type + " to " + dest + " from " + env.getProcessId());
        MyMessage msg = new MyMessage(time, type);
        env.send(dest, msg);
        if (type != REQ) {
            time += 1;
        }
    }

    private void tryEnter() {
        if (oks == env.getNumberOfProcesses() - 1 && !requests.isEmpty() && requests.peek().pid() == env.getProcessId()) {
            requests.poll();
            env.lock();
            oks = -1;
        }
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        if (message instanceof MyMessage msg) {
            time = Math.max(time, msg.time + 1);
            if (msg.type == REL) {
                requests.remove(null);
                if (requests.isEmpty() || requests.peek().pid() != sourcePid) {
                    requests.remove(new Request(-1, sourcePid));
                } else {
                    requests.poll();
                }
                tryEnter();
                return;
            }
            if (msg.type == REQ) {
                requests.add(new Request(msg.time, sourcePid));
                send(OK, sourcePid);
                return;
            }
            if (msg.type == OK) {
                ++oks;
                tryEnter();
                return;
            }
        }
        throw new UnsupportedOperationException("Unexpected message");
    }

    @Override
    public void onLockRequest() {
        oks = 0;
        int procs = env.getNumberOfProcesses();
        requests.add(new Request(time, env.getProcessId()));
        for (int i = 1; i <= procs; ++i) {
            if (env.getProcessId() == i) {
                continue;
            }
            send(REQ, i);
        }
        ++time;
        if (procs == 1) {
            tryEnter();
        }
    }

    @Override
    public void onUnlockRequest() {
        env.unlock();
        int procs = env.getNumberOfProcesses();
        for (int i = 1; i <= procs; ++i) {
            if (env.getProcessId() == i) {
                continue;
            }
            send(REL, i);
        }
    }

    private record Request(int time, int pid) implements Comparable<Request> {
        @Override
        public int compareTo(@NotNull Request o) {
            if (o.time != time) {
                return Integer.compare(time, o.time);
            }
            return Integer.compare(pid, o.pid);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Request r && r.pid == pid();
        }
    }

    private record MyMessage(int time, byte type) implements java.io.Serializable {}

    private static final byte REQ = 1;
    private static final byte REL = 2;
    private static final byte OK = 3;
}
