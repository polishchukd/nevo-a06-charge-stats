package com.nevo.chargestats;

import android.content.Context;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Доступ к VHAL через рефлексию (android.car есть в рантайме ГУ, но не в android.jar).
 * Методы getIntArrayProperty(II)/getIntProperty(II) подтверждены в smali Энергоцентра
 * и живым прогоном ChargeProbe v2.0. Только чтение.
 */
public final class Vhal {
    private Object car;
    private Object propMgr;
    private volatile boolean connected;

    public boolean isConnected() { return connected; }

    public synchronized void connect(Context ctx) throws Exception {
        Class<?> carClass = Class.forName("android.car.Car");
        Class<?> listenerClass = Class.forName("android.car.Car$CarServiceLifecycleListener");
        long waitMs;
        try { waitMs = carClass.getField("CAR_WAIT_TIMEOUT_WAIT_FOREVER").getLong(null); }
        catch (NoSuchFieldException e) { waitMs = 10_000L; }
        final AtomicReference<Object> avail = new AtomicReference<>();
        InvocationHandler ih = new InvocationHandler() {
            @Override public Object invoke(Object p, Method m, Object[] a) {
                if (m.getName().equals("onCarAvailable") && a != null && a.length > 0) avail.set(a[0]);
                return null;
            }
        };
        Object listener = Proxy.newProxyInstance(
                listenerClass.getClassLoader(), new Class<?>[]{listenerClass}, ih);
        Method create = carClass.getMethod("createCar", Context.class, android.os.Handler.class,
                long.class, listenerClass);
        Object c = create.invoke(null, ctx, null, waitMs, listener);
        car = avail.get() != null ? avail.get() : c;
        if (car == null) throw new IllegalStateException("Car.createCar null");
        propMgr = carClass.getMethod("getCarManager", String.class).invoke(car, "property");
        if (propMgr == null) throw new IllegalStateException("no property manager");
        connected = true;
    }

    public int[] readIntArray(int propId) throws Exception {
        try {
            Method m = propMgr.getClass().getMethod("getIntArrayProperty", int.class, int.class);
            return (int[]) m.invoke(propMgr, propId, 0);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable t = e.getCause() == null ? e : e.getCause();
            throw new Exception(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
