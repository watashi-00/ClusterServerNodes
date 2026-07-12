package hexacloud.core.server.route;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import hexacloud.core.utils.DebugUtils;

public class RouteRegistry {

    private final Map<String, BiConsumer<String, PrintWriter>> routes = new HashMap<>();

    public void registerController(RouteController controller) {
        if(controller == null) return;
        
        Class<?> clazz = controller.getClass();
        for(Method method : clazz.getDeclaredMethods()) {
            if(method.isAnnotationPresent(RouteMapping.class)) {
                RouteMapping mapping = method.getAnnotation(RouteMapping.class);
                String command = mapping.value().toUpperCase();
                
                Class<?>[] paramTypes = method.getParameterTypes();
                if(paramTypes.length == 2 && paramTypes[0] == String.class && paramTypes[1] == PrintWriter.class) {
                    method.setAccessible(true);
                    BiConsumer<String, PrintWriter> handler = (args, out) -> {
                        try {
                            method.invoke(controller, args, out);
                        } catch(Exception e) {
                            DebugUtils.error("Failed to invoke route method: " + method.getName(), e);
                            out.println("ERROR: Internal server error");
                        }
                    };
                    routes.put(command, handler);
                    DebugUtils.log("RouteScanner: Registered command '" + command + "' mapping to method " + clazz.getSimpleName() + "." + method.getName());
                } else {
                    DebugUtils.error("RouteScanner: Failed to register method " + clazz.getSimpleName() + "." + method.getName() + " -> Must accept parameters (String, PrintWriter)");
                }
            }
        }
    }

    public Map<String, BiConsumer<String, PrintWriter>> getRoutes() {
        return routes;
    }
}
