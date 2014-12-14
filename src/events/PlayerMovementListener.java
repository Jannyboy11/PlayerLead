package events;

import java.util.Arrays;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import playerlead.PlayerLead;

public class PlayerMovementListener implements Listener {

	private PlayerLead plugin;

	public PlayerMovementListener(PlayerLead pl){
		plugin = pl;
	}

	@EventHandler
	public void onPlayerMoveEvent(PlayerMoveEvent e) {
		Player mover = e.getPlayer();
		UUID moverUUID = mover.getUniqueId();
		slaveMasterCheck(moverUUID, e);
		horseMoveCheck(mover);
		
	}
	
	public void horseMoveCheck(Player p) {
		if (plugin.horsePlayerPair.keySet().contains(p)) {
			Horse horsey = plugin.horsePlayerPair.get(p);
			horsey.teleport(p);
			try {
				horsey.getLeashHolder();
			} catch (IllegalStateException e) {
				horsey.setLeashHolder(PlayerLead.server.getPlayer(
						plugin.slaveMasters.get(p.getUniqueId())));
			}
			
		}
	}
	public void slaveMasterCheck(UUID moverUUID, PlayerMoveEvent e) {
		for (Entry<UUID,UUID> entry : plugin.slaveMasters.entrySet()) {
			if (entry.getKey().equals(moverUUID) || entry.getValue().equals(moverUUID)) {
				Player slave = PlayerLead.server.getPlayer(entry.getKey());
				Player master = PlayerLead.server.getPlayer(entry.getValue());
				double[] info = calculateDistanceCoefficient(slave,master);
				if (info[0] > PlayerLead.maxDistance*4) {
					e.setCancelled(true);
					return;
				}
				if (info[0] > PlayerLead.maxDistance) {
					moveSlave(slave,master,info);
				}
			}
		}
	}
	
	
	/**
	 * Calculates the distance from the <code>master</code> and <code>slave</code> using Pythagoras. <br>
	 * calculates the coefficient of Z expressed in X with z/x <br>
	 * and calculates whether the slave delta coordinates is in negative x and z.
	 * @param slave
	 * @param master
	 * @return a double array of length 4, where [0] is the distance between two players <br>
	 * and [1] is the coefficient of Z/X -> Z expressed in X <br>
	 * [2] is -1 if slave is on a lower X then master, otherwise 1 <br>
	 * [3] is -1 if slave is on a lower Z then master, otherwise 1
	 */
	public double[] calculateDistanceCoefficient(Player slave, Player master) {
		double slaveX = slave.getLocation().getX();
		double slaveZ = slave.getLocation().getZ();
		double masterX = master.getLocation().getX();
		double masterZ = master.getLocation().getZ();
		double xDifference = Math.abs(Math.abs(masterX) - Math.abs(slaveX));
		double zDifference = Math.abs(Math.abs(masterZ) - Math.abs(slaveZ));
		double[] result = new double[4];
		result[0] = Math.sqrt(Math.pow(zDifference, 2) + Math.pow(xDifference, 2));
		result[1] = zDifference/xDifference;
		result[2] = slaveX < masterX ? -1 : 1;
		result[3] = slaveZ < masterZ ? -1 : 1;
		return result;
	}
	/**
	 * Moves a {@link Player Player slave} to a distance of {@link PlayerLead.maxDistance} from {@link Player Player master} <br>
	 * this function preserves the angle at which the slave/master were by using the coefficient and <br>
	 * whether the delta x/z were negative as giving in inf[2] and inf[3]
	 * @param slave the slave Player
	 * @param master the master Player
	 * @param inf the info as given by {@link #calculateDistanceCoefficient(Player, Player)}
	 */
	public void moveSlave(Player slave, Player master,double[] inf) {
		double coefficient = inf[1];
		double max = PlayerLead.maxDistance;
		// MAX^2 = X^2 + Z^2
		// Z = coefficient*x
		// MAX^2 = X^2 + (coefficient*X)^2
		//so MAX = (1+coefficient^2) X
		double amountOfX = Math.sqrt(1+Math.pow(coefficient, 2));
		double newDeltaX = max/amountOfX;
		double newDeltaZ = newDeltaX*coefficient;
		plugin.server.broadcastMessage("in the move!");
		slave.getLocation().setX(master.getLocation().getX() + newDeltaX*inf[2]);
		slave.getLocation().setZ(master.getLocation().getZ() + newDeltaZ*inf[3]);
		slave.getLocation().setY(calculateSlaveY(slave,master));
		
		
	}
	
	public double calculateSlaveY(Player slave, Player master) {
		double x = slave.getLocation().getX();
		double z = slave.getLocation().getZ();
		double lowest = Math.min(slave.getLocation().getY(), master.getLocation().getY());
		Block b =slave.getWorld().getBlockAt((int)x,(int)lowest,(int)z);
		int i;
		for (i= 0;(b == null || b.getType().equals(Material.AIR)) &&
				Math.abs(slave.getLocation().getY() - master.getLocation().getY()) > 8 && i <20; i++)
			lowest++;
		if (i > 20 && calculateDistanceCoefficient(slave,master)[0] < 2) {
			slave.getLocation().setX(master.getLocation().getX());
			slave.getLocation().setZ(master.getLocation().getZ());
			return slave.getLocation().getY();
		}
			
		return lowest;
	}
}
