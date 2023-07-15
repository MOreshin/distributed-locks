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
public class ProcessCentralized implements MutexProcess {
    private final Environment env;
    private final boolean isCoord;
    private boolean COORD_available = true;
    private final Queue<Integer> COORD_waitingForLock;

    public ProcessCentralized(Environment env) {
        this.env = env;
        this.isCoord = env.getProcessId() == 1;
        this.COORD_waitingForLock = this.isCoord ? new LinkedList<>() : null;
    }

    private void COORD_sendNext() {
        if (!isCoord) {
            throw new UnsupportedOperationException("Not Coordinator");
        }
        if (!COORD_available) {
            return;
        }
        if (!COORD_waitingForLock.isEmpty()) {
            int val = COORD_waitingForLock.poll();
            if (val != 1) {
                env.send(val, MyMessage.OK);
            } else {
                env.lock();
            }
            COORD_available = false;
        }
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        if (!isCoord && sourcePid == 1 && message == MyMessage.OK) {
            env.lock();
            return;
        }
        if (isCoord && message == MyMessage.REL) {
            COORD_available = true;
            COORD_sendNext();
            return;
        }
        if (isCoord && message == MyMessage.REQ) {
            COORD_waitingForLock.add(sourcePid);
            COORD_sendNext();
            return;
        }
        throw new UnsupportedOperationException("Unexpected message from " + sourcePid + ": " + message);
    }

    @Override
    public void onLockRequest() {
        if (isCoord) {
            COORD_waitingForLock.add(1);
            COORD_sendNext();
        } else {
            env.send(1, MyMessage.REQ);
        }
    }

    @Override
    public void onUnlockRequest() {
        env.unlock();
        if (!isCoord) {
            env.send(1, MyMessage.REL);
        } else {
            COORD_available = true;
            COORD_sendNext();
        }
    }

    private enum MyMessage {OK, REQ, REL};
}
