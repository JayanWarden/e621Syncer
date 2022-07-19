package e621Syncer.logic;

import java.io.File;

import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy;

public class JWDiscoveryStrategy implements NativeDiscoveryStrategy {

	@Override
	public boolean supported() {
		return true;
	}

	@Override
	public String discover() {
		File oFile = new File("lib\\vlc");

		return oFile.getAbsolutePath();
	}

	@Override
	public boolean onFound(String path) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean onSetPluginPath(String path) {
		// TODO Auto-generated method stub
		return true;
	}

}
