package com.butchmarshall.reactnative.google.nearby.connection;

import static android.os.Process.THREAD_PRIORITY_AUDIO;
import static android.os.Process.setThreadPriority;
import static com.butchmarshall.reactnative.google.nearby.connection.Constants.TAG;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaRecorder.AudioSource;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.IOException;
import java.io.OutputStream;

/**
 * When created, you must pass a {@link ParcelFileDescriptor}. Once {@link #start()} is called, the
 * file descriptor will be written to until {@link #stop()} is called.
 */
public class AudioRecorder {
	/** The stream to write to. */
	private final OutputStream mOutputStream;

	/**
	 * If true, the background thread will continue to loop and record audio. Once false, the thread
	 * will shut down.
	 */
	private volatile boolean mAlive;

	/** The background thread recording audio for us. */
	private Thread mThread;

	/**
	 * A simple audio recorder.
	 *
	 * @param file The output stream of the recording.
	 */
	public AudioRecorder(ParcelFileDescriptor file) {
		mOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(file);
	}

	/** @return True if actively recording. False otherwise. */
	public boolean isRecording() {
		return mAlive;
	}

	public AudioRecord findAudioRecord() {
		for (int rate : AudioBuffer.POSSIBLE_SAMPLE_RATES) {
			for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT }) {
				for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO }) {
					try {
						Log.d(TAG, "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: "
								+ channelConfig);
						int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

						if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
							// check if we can instantiate and have a success
							AudioRecord recorder = new AudioRecord(AudioSource.DEFAULT, rate, channelConfig, audioFormat, bufferSize);

							if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
								return recorder;
							}
						}
					} catch (Exception e) {
						Log.e(TAG, rate + "Exception, keep trying.",e);
					}
				}
			}
		}
		return null;
	}

	/** Starts recording audio. */
	public void start() {
		if (isRecording()) {
			Log.w(TAG, "Already running");
			return;
		}

		mAlive = true;
		mThread =
			new Thread() {
				@Override
				public void run() {
					setThreadPriority(THREAD_PRIORITY_AUDIO);

					Buffer buffer = new Buffer();
					AudioRecord record = findAudioRecord();

					if (record == null) {
						Log.w(TAG, "Failed to start recording");
						mAlive = false;
						return;
					}

					record.startRecording();

					// While we're running, we'll read the bytes from the AudioRecord and write them
					// to our output stream.
					try {
						while (isRecording()) {
							int len = record.read(buffer.data, 0, buffer.size);
							if (len >= 0 && len <= buffer.size) {
								mOutputStream.write(buffer.data, 0, len);
								mOutputStream.flush();
							} else {
								throw new IOException("AudioRecorder - Unexpected length returned: " + len);
							}
						}
					} catch (IOException e) {
						Log.e(TAG, "Exception with recording stream", e);
					} finally {
						try {
							record.stop();
						} catch (IllegalStateException e) {
							Log.e(TAG, "Failed to stop AudioRecord", e);
						}
						record.release();
					}
				}
			};
		mThread.start();
	}

	private void closeStream() {
		try {
			mOutputStream.close();
		} catch (IOException e) {
			Log.e(TAG, "Failed to close output stream", e);
		}
	}

	/** Stops recording audio. */
	public void stop() {
		mAlive = false;
		closeStream();
		try {
			mThread.join();
		} catch (InterruptedException e) {
			Log.e(TAG, "Interrupted while joining AudioRecorder thread", e);
			Thread.currentThread().interrupt();
		}
	}

	private static class Buffer extends AudioBuffer {
		@Override
		protected boolean validSize(int size) {
			return size != AudioRecord.ERROR && size != AudioRecord.ERROR_BAD_VALUE;
		}

		@Override
		protected int getMinBufferSize(int sampleRate) {
			return AudioRecord.getMinBufferSize(
				sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		}
	}
}