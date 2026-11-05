package net.vanfleteren.jazygit.image;

/**
 * The pixels of an image as it was read, each as ARGB, row by row; only while it is made into a {@link Picture}.
 */
record Raster(int width, int height, int[] argb) {}
