package pl.iomreborn.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.iomreborn.util.Tasks;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Asks a player to type a value in the chat (used to rename maps from the GUI). */
public final class ChatPrompts implements Listener {

    private static final long TIMEOUT_MILLIS = 120_000L;
    private static final Set<String> CANCEL_WORDS = Set.of("cancel", "anuluj", "-");

    private record Prompt(Consumer<String> onAnswer, Runnable onCancel, long expiresAt) {
    }

    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    /** The callbacks run on the main thread. Typing "cancel" (or "anuluj") calls onCancel. */
    public void ask(Player player, Consumer<String> onAnswer, Runnable onCancel) {
        prompts.put(player.getUniqueId(), new Prompt(onAnswer, onCancel, System.currentTimeMillis() + TIMEOUT_MILLIS));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Prompt prompt = prompts.remove(event.getPlayer().getUniqueId());
        if (prompt == null || prompt.expiresAt() < System.currentTimeMillis()) {
            return;
        }
        event.setCancelled(true);
        String answer = event.getMessage().trim();
        boolean cancelled = CANCEL_WORDS.contains(answer.toLowerCase(Locale.ROOT));
        Tasks.sync(() -> {
            if (cancelled) {
                prompt.onCancel().run();
            } else {
                prompt.onAnswer().accept(answer);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }
}
