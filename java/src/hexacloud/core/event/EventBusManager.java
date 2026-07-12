package hexacloud.core.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import hexacloud.core.utils.DebugUtils;

public class EventBusManager {
    private final Map<Class<? extends Event>, List<EventListener<?>>> channels = new HashMap<>();

    public <T extends Event> void sub(Class<T> eventType, EventListener<T> listener) {
        channels.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    } 

    @SuppressWarnings("unchecked")
    public <T extends Event> void dispatch(T event) {
        Class<? extends Event> eventType = event.getClass();
        List<EventListener<?>> listeners = channels.get(eventType);

        DebugUtils.log("Dispatching global event: " + event);

        if(listeners != null) {
            for(var listener : listeners) {
                ((EventListener<T>) listener).onEvent(event);
            }
        }
    }
}
