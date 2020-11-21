import org.openjdk.jol.info.ClassLayout;


public class A {

	public final static class VolatileLong {
		// header 12b
		public volatile boolean value = false; // 13b
		public volatile byte b010, b011, b012; // 16b
		public volatile byte b020, b021, b022, b023, b024, b025, b026, b027;// 24b
		public volatile byte b030, b031, b032, b033, b034, b035, b036, b037;// 32b
		public volatile byte b040, b041, b042, b043, b044, b045, b046, b047;// 40b
		public volatile byte b050, b051, b052, b053, b054, b055, b056, b057;// 48b
		public volatile byte b060, b061, b062, b063, b064, b065, b066, b067;// 56b
		public volatile byte b070, b071, b072, b073, b074, b075, b076, b077;// 64b
		public volatile byte b100, b101, b102, b103, b104, b105, b106, b107;// 72b
		public volatile byte b110, b111, b112, b113, b114, b115, b116, b117;// 80b
		public volatile byte b120, b121, b122, b123, b124, b125, b126, b127;// 88b
		public volatile byte b130, b131, b132, b133, b134, b135, b136, b137;// 96b
		public volatile byte b140, b141, b142, b143, b144, b145, b146, b147;// 104b
		public volatile byte b150, b151, b152, b153, b154, b155, b156, b157;// 112b
		public volatile byte b160, b161, b162, b163, b164, b165, b166, b167;// 120b
		public volatile byte b170, b171, b172, b173, b174, b175, b176, b177;// 128b
	}

	public static void main(String[] args) {

		System.out.println(ClassLayout.parseClass(VolatileLong.class).toPrintable());

	}
}
