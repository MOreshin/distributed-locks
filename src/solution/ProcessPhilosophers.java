package solution;

import internal.Environment;

/**
 * Distributed mutual exclusion implementation.
 * All functions are called from the single main thread.
 *
 * @author Mikhail Oreshin
 */
public class ProcessPhilosophers implements MutexProcess {
    private final Environment env;
    private final ForkState[] ownThis; // 0 and curpid are meaningless
    private final int[] ignoreTakes;
    private int owned = 0;
    private boolean hungry = false;
    private boolean eating = false;

    public ProcessPhilosophers(Environment env) {
        //System.out.println(env.getNumberOfProcesses() + " PROCS");
        this.env = env;
        ignoreTakes = new int[env.getNumberOfProcesses() + 1];
        ownThis = new ForkState[env.getNumberOfProcesses() + 1];
        for (int i = 1; i <= env.getNumberOfProcesses(); ++i) {
            ownThis[i] = env.getProcessId() < i ? ForkState.OWN : ForkState.NOTOWN;
            if (ownThis[i] == ForkState.OWN) {
                ++owned;
            }
        }
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        //System.out.println(env.getProcessId() + " got msg from " + sourcePid + ": " + message);
        if (message instanceof MyMessage) {
            if (message == MyMessage.TG) {
                onMessage(sourcePid, MyMessage.TAKE);
                onMessage(sourcePid, MyMessage.GIVE);
                return;
            }
            if (message == MyMessage.TAKE) {
                if (ignoreTakes[sourcePid] > 0) {
                    --ignoreTakes[sourcePid];
                    return;
                }
                if (ownThis[sourcePid] != ForkState.NOTOWN) {
                    throw new UnsupportedOperationException("??");
                }
                ownThis[sourcePid] = ForkState.OWN;
                ++owned;
                if (hungry && owned == env.getNumberOfProcesses() - 1) {
                    //System.out.println(env.getProcessId() + " normal locking");
                    eating = true;
                    env.lock();
                }
                return;
            }
            if (message == MyMessage.GIVE) {
                //validation: can we give it?
                if (ownThis[sourcePid] == ForkState.NOTOWN) {
                    //missed a take call
                    //System.out.println("generating take:");
                    int takesIg = ignoreTakes[sourcePid];
                    ignoreTakes[sourcePid] = 0;
                    onMessage(sourcePid, MyMessage.TAKE);
                    ignoreTakes[sourcePid] = takesIg + 1;
                    //proceed normally
                }
                if (!hungry && ownThis[sourcePid] == ForkState.OWN) {
                    for (int i = 1; i <= env.getNumberOfProcesses(); ++i) {
                        if (ownThis[i] == ForkState.OWN && i != env.getProcessId()) {
                            ownThis[i] = ForkState.DIRTY;
                        }
                    }
                }
                if (!eating && ownThis[sourcePid] == ForkState.DIRTY) {
                    if (!hungry) {
                        env.send(sourcePid, MyMessage.TAKE);
                    } else {
                        env.send(sourcePid, MyMessage.TG);
                    }
                    ownThis[sourcePid] = ForkState.NOTOWN;
                    --owned;
                    return;
                }
                ownThis[sourcePid] = ForkState.OWNGIVE;
                return;
            }
        }
        throw new UnsupportedOperationException("Unexpected message: " + message);
    }

    @Override
    public void onLockRequest() {
        //System.out.println(env.getProcessId() + " wants lock");
        hungry = true;
        if (owned == env.getNumberOfProcesses() - 1) {
            //System.out.println(env.getProcessId() + " autolocking");
            eating = true;
            env.lock();
            return;
        }
        for (int i = 1; i <= env.getNumberOfProcesses(); ++i) {
            if (env.getProcessId() == i) {
                continue;
            }
            if (ownThis[i] == ForkState.NOTOWN) {
                env.send(i, MyMessage.GIVE);
            }
        }
    }

    @Override
    public void onUnlockRequest() {
        //System.out.println(env.getProcessId() + " unlocking");
        hungry = false;
        eating = false;
        env.unlock();
        for (int i = 1; i <= env.getNumberOfProcesses(); ++i) {
            if (i == env.getProcessId()) {
                continue;
            }
            if (ownThis[i] == ForkState.OWNGIVE) {
                ownThis[i] = ForkState.NOTOWN;
                env.send(i, MyMessage.TAKE);
                --owned;
            } else {
                ownThis[i] = ForkState.DIRTY;
            }
        }
    }

    private enum ForkState {
        OWN, NOTOWN, DIRTY, OWNGIVE
    }

    private enum MyMessage {
        GIVE, TAKE, TG
    }
}