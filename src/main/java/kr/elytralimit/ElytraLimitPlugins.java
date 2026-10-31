package kr.elytralimit;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 활강(겉날개) 중인 플레이어의 실제 이동 속도를 매 틱 측정해서 제한합니다.
 * Player#getVelocity()는 플레이어에게 정확하지 않아서 위치 변화량으로 계산합니다.
 * 주의: 컴파일/실행 테스트를 하지 않았습니다.
 */
public final class ElytraLimitPlugin extends JavaPlugin implements Listener {
    private static final double TELEPORT_THRESHOLD = 10.0; // 한 틱에 이 이상 움직이면 텔레포트로 간주

    private final Map<UUID, Location> last = new HashMap<>();
    private double maxBps;
    private boolean notify;
    private Component notifyMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        load();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    }

    private void load() {
        reloadConfig();
        maxBps = Math.max(1.0, getConfig().getDouble("max-speed-bps", 40.0));
        notify = getConfig().getBoolean("notify", true);
        notifyMessage = Component.text(getConfig().getString("notify-message", "겉날개 속도 제한"));
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!p.isGliding() || p.hasPermission("elytralimit.bypass")) {
                last.remove(id);
                continue;
            }
            Location now = p.getLocation();
            Location prev = last.put(id, now);
            if (prev == null || prev.getWorld() != now.getWorld()) continue;

            Vector delta = now.toVector().subtract(prev.toVector());
            double len = delta.length();
            if (len < 1.0e-6 || len > TELEPORT_THRESHOLD) continue;

            double perTickMax = maxBps / 20.0;
            if (len > perTickMax) {
                p.setVelocity(delta.multiply(perTickMax / len));
                if (notify) p.sendActionBar(notifyMessage);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("elytralimit.admin")) {
                sender.sendMessage("권한이 없습니다.");
                return true;
            }
            load();
            sender.sendMessage("ElytraLimit 설정을 다시 불러왔습니다. (최대 " + maxBps + " 블록/초)");
            return true;
        }
        return false;
    }
}
