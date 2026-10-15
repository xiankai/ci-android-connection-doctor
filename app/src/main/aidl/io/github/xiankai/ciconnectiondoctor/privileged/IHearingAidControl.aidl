package io.github.xiankai.ciconnectiondoctor.privileged;

interface IHearingAidControl {
    void destroy() = 16777114;

    int[] snapshot(String address) = 1;

    boolean setAshaAllowed(String address, boolean allowed) = 2;

    boolean connectAsha(String address) = 3;

    boolean restartCochlearApp() = 4;

    boolean stopCochlearApp() = 5;

    void exitWhenClientDies(IBinder client) = 6;

    void keepPauseWithoutGuard() = 7;
}
