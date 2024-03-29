package net.srcdemo.audio.factories;

import net.srcdemo.SrcDemo;
import net.srcdemo.audio.AudioHandler;
import net.srcdemo.audio.AudioHandlerFactory;
import net.srcdemo.audio.DiskAudioHandler;

public class DiskAudioHandlerFactory extends AudioHandlerFactory {
	@Override
	public AudioHandler buildHandler(final SrcDemo demo) {
		return new DiskAudioHandler(demo);
	}
}
