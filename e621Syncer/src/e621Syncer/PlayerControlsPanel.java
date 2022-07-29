/*
 * This file is part of VLCJ.
 *
 * VLCJ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VLCJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VLCJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2009-2019 Caprica Software Limited.
 */

package e621Syncer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import uk.co.caprica.vlcj.player.base.LibVlcConst;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

@SuppressWarnings("serial")
public class PlayerControlsPanel extends JPanel {

	private static final int SKIP_TIME_MS = 10 * 1000;

	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

	private final EmbeddedMediaPlayer mediaPlayer;

	private JLabel timeLabel;
	private JSlider positionSlider;

	private JButton rewindButton;
	private JButton stopButton;
	private JButton pauseButton;
	private JButton playButton;
	private JButton fastForwardButton;

	private JButton toggleMuteButton;
	private JSlider volumeSlider;
	private JLabel labelIcon;

	private boolean mousePressedPlaying = false;

	public PlayerControlsPanel(EmbeddedMediaPlayer mediaPlayer) {
		this.mediaPlayer = mediaPlayer;

		createUI();

		executorService.scheduleAtFixedRate(new UpdateRunnable(mediaPlayer), 0L, 1L, TimeUnit.SECONDS);
	}

	private void createUI() {
		createControls();
		layoutControls();
		registerListeners();
	}

	private void createControls() {
		timeLabel = new JLabel("hh:mm:ss");

		positionSlider = new JSlider();
		positionSlider.setMinimum(0);
		positionSlider.setMaximum(1000);
		positionSlider.setValue(0);
		positionSlider.setToolTipText("Position");

		rewindButton = new JButton();
		rewindButton.setIcon(new ImageIcon("icons/control_rewind_blue.png"));
		rewindButton.setToolTipText("Skip back");

		stopButton = new JButton();
		stopButton.setIcon(new ImageIcon("icons/control_stop_blue.png"));
		stopButton.setToolTipText("Stop");

		pauseButton = new JButton();
		pauseButton.setIcon(new ImageIcon("icons/control_pause_blue.png"));
		pauseButton.setToolTipText("Play/pause");

		playButton = new JButton();
		playButton.setIcon(new ImageIcon("icons/control_play_blue.png"));
		playButton.setToolTipText("Play");

		fastForwardButton = new JButton();
		fastForwardButton.setIcon(new ImageIcon("icons/control_fastforward_blue.png"));
		fastForwardButton.setToolTipText("Skip forward");

		toggleMuteButton = new JButton();
		toggleMuteButton.setIcon(new ImageIcon("icons/sound_mute.png"));
		toggleMuteButton.setToolTipText("Toggle Mute");

		volumeSlider = new JSlider();
		volumeSlider.setOrientation(JSlider.HORIZONTAL);
		volumeSlider.setMinimum(LibVlcConst.MIN_VOLUME);
		volumeSlider.setMaximum(LibVlcConst.MAX_VOLUME);
		volumeSlider.setPreferredSize(new Dimension(100, 40));
		volumeSlider.setToolTipText("Change volume");

		labelIcon = new JLabel();
		labelIcon.setIcon(new ImageIcon("icons/vlcj-logo.png"));
	}

	private void layoutControls() {
		setBorder(new EmptyBorder(4, 4, 4, 4));

		setLayout(new BorderLayout());

		JPanel positionPanel = new JPanel();
		positionPanel.setLayout(new GridLayout(1, 1));
		positionPanel.add(positionSlider);

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BorderLayout(8, 0));

		topPanel.add(timeLabel, BorderLayout.WEST);
		topPanel.add(positionPanel, BorderLayout.CENTER);

		add(topPanel, BorderLayout.NORTH);

		JPanel bottomPanel = new JPanel();

		bottomPanel.setLayout(new FlowLayout());

		bottomPanel.add(labelIcon);
		bottomPanel.add(rewindButton);
		bottomPanel.add(stopButton);
		bottomPanel.add(pauseButton);
		bottomPanel.add(playButton);
		bottomPanel.add(fastForwardButton);

		bottomPanel.add(volumeSlider);
		bottomPanel.add(toggleMuteButton);

		add(bottomPanel, BorderLayout.SOUTH);
	}

	/**
	 * Broken out position setting, handles updating mediaPlayer
	 */
	private void setSliderBasedPosition() {
		if (!mediaPlayer.status().isSeekable()) {
			return;
		}
		float positionValue = positionSlider.getValue() / 1000.0f;
		// Avoid end of file freeze-up
		if (positionValue > 0.99f) {
			positionValue = 0.99f;
		}
		mediaPlayer.controls().setPosition(positionValue);
	}

	private void updateUIState() {
		if (!mediaPlayer.status().isPlaying()) {
			// Resume play or play a few frames then pause to show current position in video
			mediaPlayer.controls().play();
			if (!mousePressedPlaying) {
				try {
					// Half a second probably gets an iframe
					Thread.sleep(500);
				} catch (InterruptedException e) {
					// Don't care if unblocked early
				}
				mediaPlayer.controls().pause();
			}
		}
		long time = mediaPlayer.status().time();
		int position = (int) (mediaPlayer.status().position() * 1000.0f);
		updateTime(time);
		updatePosition(position);
	}

	private void skip(int skipTime) {
		// Only skip time if can handle time setting
		if (mediaPlayer.status().length() > 0) {
			mediaPlayer.controls().skipTime(skipTime);
			updateUIState();
		}
	}

	private void registerListeners() {
		mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
			@Override
			public void playing(MediaPlayer mediaPlayer) {
//                updateVolume(mediaPlayer.getVolume());
			}
		});

		positionSlider.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (mediaPlayer.status().isPlaying()) {
					mousePressedPlaying = true;
					mediaPlayer.controls().pause();
				} else {
					mousePressedPlaying = false;
				}
				setSliderBasedPosition();
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				setSliderBasedPosition();
				updateUIState();
			}
		});

		rewindButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				skip(-SKIP_TIME_MS);
			}
		});

		stopButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				mediaPlayer.controls().stop();
			}
		});

		pauseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				mediaPlayer.controls().pause();
			}
		});

		playButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				mediaPlayer.controls().play();
			}
		});

		fastForwardButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				skip(SKIP_TIME_MS);
			}
		});

		toggleMuteButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				mediaPlayer.audio().mute();
			}
		});

		volumeSlider.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				JSlider source = (JSlider) e.getSource();
				// if(!source.getValueIsAdjusting()) {
				mediaPlayer.audio().setVolume(source.getValue());
				// }
			}
		});
	}

	private final class UpdateRunnable implements Runnable {

		private final MediaPlayer mediaPlayer;

		private UpdateRunnable(MediaPlayer mediaPlayer) {
			this.mediaPlayer = mediaPlayer;
		}

		@Override
		public void run() {
			final long time = mediaPlayer.status().time();
			final int position = (int) (mediaPlayer.status().position() * 1000.0f);

			// Updates to user interface components must be executed on the Event
			// Dispatch Thread
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					if (mediaPlayer.status().isPlaying()) {
						updateTime(time);
						updatePosition(position);
					}
				}
			});
		}
	}

	private void updateTime(long millis) {
		String s = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
				TimeUnit.MILLISECONDS.toMinutes(millis)
						- TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
				TimeUnit.MILLISECONDS.toSeconds(millis)
						- TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
		timeLabel.setText(s);
	}

	private void updatePosition(int value) {
		// positionProgressBar.setValue(value);
		positionSlider.setValue(value);
	}
}