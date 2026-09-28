package dev.fallingcloud.slate.earlywindow;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import net.neoforged.fml.earlydisplay.RenderElement;

/**
 * One of FML's {@link RenderElement}s that paints with {@code paint} instead of FML's buffer builder. FML keeps the
 * element's renderer and initializer interfaces package-private, so they are implemented with proxies (the proxy
 * classes are defined in FML's own package, which an automatic module leaves open).
 */
final class SceneElement {

    private static final String RENDERER = "net.neoforged.fml.earlydisplay.RenderElement$Renderer";
    private static final String INITIALIZER = "net.neoforged.fml.earlydisplay.RenderElement$Initializer";

    static RenderElement create(final Consumer<RenderElement.DisplayContext> paint) throws ReflectiveOperationException {
        final ClassLoader loader = RenderElement.class.getClassLoader();
        final Class<?> rendererType = Class.forName(RENDERER, false, loader);
        final Class<?> initializerType = Class.forName(INITIALIZER, false, loader);
        // Renderer.accept(SimpleBufferBuilder, DisplayContext, int frame)
        final Object renderer = proxy(loader, rendererType, "accept", args -> {
            paint.accept((RenderElement.DisplayContext) args[1]);
            return null;
        });
        // Initializer.get() -> Renderer
        final Object initializer = proxy(loader, initializerType, "get", args -> renderer);
        return (RenderElement) RenderElement.class.getConstructor(initializerType).newInstance(initializer);
    }

    private interface Call {
        Object run(Object[] args);
    }

    private static Object proxy(final ClassLoader loader, final Class<?> type, final String method, final Call call) {
        final InvocationHandler handler = (self, m, args) -> switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "toString" -> "Slate start-up screen";
            default -> {
                if (!m.getName().equals(method)) throw new UnsupportedOperationException(m.getName());
                yield call.run(args);
            }
        };
        return Proxy.newProxyInstance(loader, new Class<?>[] {type}, handler);
    }

    private SceneElement() {}
}
