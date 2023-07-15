package solution;

import internal.Environment;

import java.io.Serializable;

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Mikhail Oreshin
 */
public class ProcessToken implements MutexProcess {
    private final Environment env;
    private boolean wantLock = false;
    private static final Token token = new Token();

    public ProcessToken(Environment env) {
        this.env = env;
        if (env.getProcessId() == 1) {
            env.send(2, token);
        }
    }

    private int next() {
        if (env.getNumberOfProcesses() == env.getProcessId()) {
            return 1;
        }
        return env.getProcessId() + 1;
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        if (message instanceof Token) {
            if (wantLock) {
                env.lock();
            } else {
                env.send(next(), token);
            }
            return;
        }
        throw new UnsupportedOperationException("Unexpected message: " + message);
    }

    @Override
    public void onLockRequest() {
        wantLock = true;
    }

    @Override
    public void onUnlockRequest() {
        env.unlock();
        wantLock = false;
        env.send(next(), token);
    }

    private static class Token implements Serializable {}
}
