package net.srcdemo.test;

import java.io.File;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.trolltech.qt.core.QCoreApplication;
import com.trolltech.qt.core.QSize;
import com.trolltech.qt.core.Qt.AlignmentFlag;
import com.trolltech.qt.core.Qt.AspectRatioMode;
import com.trolltech.qt.core.Qt.TransformationMode;
import com.trolltech.qt.gui.QApplication;
import com.trolltech.qt.gui.QImage;
import com.trolltech.qt.gui.QImage.Format;
import com.trolltech.qt.gui.QLabel;
import com.trolltech.qt.gui.QPixmap;
import com.trolltech.qt.gui.QResizeEvent;
import com.trolltech.qt.gui.QSizePolicy.Policy;
import com.trolltech.qt.gui.QVBoxLayout;
import com.trolltech.qt.gui.QWidget;

final class UpdatablePictureTest {
	private static final class PictureTest extends QWidget {
		private final UpdatablePicture pic;

		private PictureTest(final int[] initial, final int width, final int height) {
			setWindowTitle("Test UpdatablePicture");
			final QVBoxLayout vbox = new QVBoxLayout();
			pic = new UpdatablePicture(initial, width, height);
			vbox.addWidget(pic);
			setLayout(vbox);
			show();
		}
	}

	public static class UpdatablePicture extends QLabel {
		private static final int imageBorderPixels = 2;
		private String image = null;
		private File initialImage = null;
		private int[] initialPixelData = null;
		private int initialPixelHeight = 0;
		private int initialPixelWidth = 0;
		private final Lock lock = new ReentrantLock();
		private Integer maxHeight = null;
		private Integer maxWidth = null;
		private boolean needUpdate = true;
		private QPixmap newPixmap = null;
		private QPixmap pixmapOriginal = null;
		private QPixmap priorResetPixmap = null;
		private int rawHeight = 0;
		private int[] rawPixelData;
		private int rawWidth = 0;
		private QSize size = null;

		public UpdatablePicture(final File initialImage) {
			this(initialImage, null, null);
		}

		public UpdatablePicture(final File initialImage, final Integer maxWidth, final Integer maxHeight) {
			this.initialImage = initialImage;
			image = initialImage.getAbsolutePath();
			postInit(maxWidth, maxHeight);
		}

		public UpdatablePicture(final int[] data, final int width, final int height) {
			this(data == null ? null : data, width, height, null, null);
		}

		public UpdatablePicture(final int[] initialData, final int width, final int height, final Integer maxWidth,
			final Integer maxHeight) {
			initialPixelData = initialData;
			initialPixelWidth = width;
			initialPixelHeight = height;
			rawPixelData = initialPixelData;
			rawWidth = width;
			rawHeight = height;
			if (rawWidth <= 0 || rawHeight <= 0 || initialData.length != rawWidth * rawHeight) {
				throw new IllegalArgumentException("Image data does not match provided image dimensions.");
			}
			postInit(maxWidth, maxHeight);
		}

		public UpdatablePicture(final String initialImage) {
			this(initialImage == null ? null : new File(initialImage), null, null);
		}

		private QSize getTargetSize() {
			if (pixmapOriginal != null) {
				size = pixmapOriginal.size();
				final QSize targetSize = new QSize(Math.min(size.width(), width() - imageBorderPixels), Math.min(size.height(),
					height() - imageBorderPixels));
				size.scale(targetSize, AspectRatioMode.KeepAspectRatio);
			}
			return size;
		}

		private void postInit(final Integer maxWidth, final Integer maxHeight) {
			this.maxWidth = maxWidth;
			this.maxHeight = maxHeight;
			setAlignment(AlignmentFlag.AlignCenter);
			setSizePolicy(Policy.MinimumExpanding, Policy.MinimumExpanding);
			setMinimumSize(1, 1);
			updatePicture();
		}

		public void push(final File newImage) {
			final String path = newImage.getAbsolutePath();
			lock.lock();
			needUpdate = needUpdate || !path.equals(image);
			image = path;
			rawPixelData = null;
			newPixmap = null;
			lock.unlock();
		}

		public void push(final int[] pixelData, final int width, final int height) {
			if (width <= 0 || height <= 0 || pixelData.length != width * height) {
				// Invalid image data.
				return;
			}
			lock.lock();
			needUpdate = true; // Comparing each pixel would be expensive; assume it did change
			image = null;
			rawPixelData = pixelData;
			newPixmap = null;
			rawWidth = width;
			rawHeight = height;
			lock.unlock();
		}

		public void push(final QPixmap pixmap) {
			if (pixmap == null || pixmap.isNull()) {
				return;
			}
			lock.lock();
			needUpdate = needUpdate || !pixmap.equals(pixmapOriginal);
			image = null;
			rawPixelData = null;
			newPixmap = pixmap;
			lock.unlock();
		}

		public void reset() {
			priorResetPixmap = pixmapOriginal;
			if (initialImage != null) {
				push(initialImage);
			} else if (initialPixelData != null) {
				push(initialPixelData, initialPixelWidth, initialPixelHeight);
			}
		}

		@Override
		protected void resizeEvent(final QResizeEvent event) {
			lock.lock();
			final QSize targetSize = getTargetSize();
			if (targetSize != null && !targetSize.equals(pixmap().size())) {
				needUpdate = true;
				updatePicture();
			}
			lock.unlock();
		}

		public void restore() {
			push(priorResetPixmap);
		}

		public void updatePicture() {
			boolean doGc = false;
			lock.lock();
			if (needUpdate) {
				if (newPixmap != null) {
					pixmapOriginal = newPixmap;
				} else if (image != null) {
					pixmapOriginal = new QPixmap(image);
				} else if (rawPixelData != null) {
					final int length = rawPixelData.length;
					int width = rawWidth;
					int height = rawHeight;
					final byte[] rgb;
					if ((maxWidth != null && rawWidth > maxWidth) || (maxHeight != null && rawHeight > maxHeight)) {
						final double inverseScale = Math.max((double) width / (double) maxWidth, (double) height
							/ (double) maxHeight);
						final double scale = 1 / inverseScale;
						width = (int) Math.floor(width * scale);
						height = (int) Math.floor(height * scale);
						int index;
						rgb = new byte[width * height * 3];
						for (int i = 0; i < width * height; i++) {
							index = Math.min((int) (inverseScale * (i / width)), rawHeight) * rawWidth
								+ Math.min((int) (inverseScale * (i % width)), rawWidth);
							rgb[i * 3] = (byte) ((rawPixelData[index] & 0xff0000) >> 16);
							rgb[i * 3 + 1] = (byte) ((rawPixelData[index] & 0xff00) >> 8);
							rgb[i * 3 + 2] = (byte) (rawPixelData[index] & 0xff);
						}
					} else {
						rgb = new byte[length * 3];
						for (int i = 0; i < length; i++) {
							rgb[i * 3] = (byte) ((rawPixelData[i] & 0xff0000) >> 16);
							rgb[i * 3 + 1] = (byte) ((rawPixelData[i] & 0xff00) >> 8);
							rgb[i * 3 + 2] = (byte) (rawPixelData[i] & 0xff);
						}
					}
					final QImage finalImage = new QImage(rgb, width, height, Format.Format_RGB888);
					if (!finalImage.isNull()) {
						pixmapOriginal = QPixmap.fromImage(finalImage);
					}
					finalImage.dispose();
					rawPixelData = null;
				}
				if (!pixmapOriginal.isNull()) {
					setPixmap(pixmapOriginal.scaled(getTargetSize(), AspectRatioMode.KeepAspectRatio,
						TransformationMode.SmoothTransformation));
				}
				needUpdate = false;
				doGc = true;
			}
			lock.unlock();
			if (doGc) {
				System.gc();
			}
		}
	}

	private static int[] getRandomBytes(final int width, final int height) {
		final int[] data = new int[width * height];
		final Random random = new Random();
		for (int i = 0; i < data.length; i++) {
			data[i] = (random.nextInt(255) << 16) + (random.nextInt(255) << 8) + random.nextInt(255);
		}
		return data;
	}

	public static void main(final String[] args) {
		final int width = 1920;
		final int height = 1080;
		final int[] data = getRandomBytes(width, height);
		QApplication.initialize(args);
		final PictureTest test = new PictureTest(data, width, height);
		new Timer("Updater", true).schedule(new TimerTask() {
			@Override
			public void run() {
				QCoreApplication.invokeLater(new Runnable() {
					@Override
					public void run() {
						test.pic.updatePicture();
					}
				});
			}
		}, 0, 150);
		final Thread pusher = new Thread() {
			@Override
			public void run() {
				int[] data;
				try {
					while (true) {
						data = getRandomBytes(width, height);
						test.pic.push(data, width, height);
						Thread.sleep(100);
					}
				}
				catch (final InterruptedException e) {
				}
			}
		};
		pusher.start();
		QCoreApplication.exec();
		pusher.interrupt();
	}
}
