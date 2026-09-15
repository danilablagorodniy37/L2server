import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import com.l2jserver.geodriver.GeoDriver;

/**
 * Reads "x y z" lines from stdin and prints "x y z nearestZ" (or "x y z -" without geodata),
 * using the same geodata driver as the game server.
 * Usage: java -cp tools/geo;game/libs/l2j-server-geo-driver-*.jar GeoHeight game/data/geodata
 */
public class GeoHeight {
	public static void main(String[] args) throws Exception {
		final GeoDriver driver = new GeoDriver();
		final Set<String> loaded = new HashSet<>();
		final Path dir = Path.of(args[0]);
		try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
			String line;
			while ((line = in.readLine()) != null) {
				final String[] p = line.trim().split("\s+");
				if (p.length < 3) {
					continue;
				}
				final int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
				final int rx = Math.floorDiv(x, 32768) + 20, ry = Math.floorDiv(y, 32768) + 18;
				final Path file = dir.resolve(rx + "_" + ry + ".l2j");
				if (loaded.add(rx + "_" + ry) && Files.exists(file)) {
					driver.loadRegion(file, rx, ry);
				}
				final int gx = driver.getGeoX(x), gy = driver.getGeoY(y);
				System.out.println(x + " " + y + " " + z + " " + (driver.hasGeoPos(gx, gy) ? driver.getNearestZ(gx, gy, z) : "-"));
			}
		}
	}
}
