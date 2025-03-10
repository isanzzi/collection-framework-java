import java.util.*;
import java.util.concurrent.*;

// 2. Record digunakan untuk merepresentasikan transaksi kripto secara immutable
//    Alasan: Record membuat objek transaksi bersifat immutable, sehingga aman untuk pemrograman konkurensi.
record CryptoTrade(String user, String asset, double amount, boolean isBuyOrder, double price) {}

public class CryptoTradingSystem {
    // 1. List digunakan untuk menyimpan riwayat transaksi secara thread-safe
    //    Alasan: Menyimpan elemen berurutan, memungkinkan duplikat, cocok untuk riwayat transaksi.
    private final List<CryptoTrade> tradeHistory = new CopyOnWriteArrayList<>();

    // 1. Set digunakan untuk menyimpan daftar aset kripto yang dimiliki pengguna tanpa duplikasi
    //    Alasan: Menggunakan Set memastikan bahwa seorang pengguna tidak memiliki aset yang sama lebih dari sekali.
    private final Map<String, Set<String>> userAssets = new ConcurrentHashMap<>();

    // 1. Map digunakan untuk menyimpan harga aset kripto secara real-time
    //    Alasan: Menyimpan pasangan key-value, cocok untuk harga aset atau mapping pengguna ke aset.
    private final Map<String, Double> cryptoPrices = new ConcurrentHashMap<>();

    // 5. Queue digunakan untuk antrean order yang belum dieksekusi
    //    Alasan: ConcurrentLinkedQueue memastikan FIFO (First-In, First-Out) dan aman digunakan dalam lingkungan multithreading.
    private final Queue<CryptoTrade> orderQueue = new ConcurrentLinkedQueue<>();

    // 6. Immutable Collection untuk daftar mata uang kripto yang tersedia
    //    Alasan: Menggunakan Set.of memastikan daftar aset ini tetap konstan dan tidak bisa dimodifikasi.
    private final Set<String> supportedCryptos = Set.of("BTC", "ETH", "ADA", "XRP", "SOL");
    //keunikan jadi pertimbangan

    // 6. Immutable Collection List.of untuk daftar pengguna awal
    //    Alasan: Menggunakan List.of memastikan daftar ini tetap konstan dan tidak dapat dimodifikasi.
    private final List<String> initialUsers = List.of("Alice", "Bob", "Charlie");
    //mungkin duplikat

    public CryptoTradingSystem() {
        // 6. Immutable Collection untuk harga awal aset
        //    Alasan: Menggunakan Map.of memastikan nilai awal harga kripto tidak bisa diubah setelah inisialisasi.
        this.cryptoPrices.putAll(Map.of(
                "BTC", 65000.0, "ETH", 3500.0, "ADA", 1.2, "XRP", 0.6, "SOL", 150.0
        ));
    }

    // Fungsi untuk menempatkan order beli atau jual
    public void placeOrder(String user, String asset, double amount, boolean isBuyOrder) {
        // 3. Menggunakan Optional untuk validasi aset
        //    Alasan: Optional membantu menghindari NullPointerException dan memberikan cara yang lebih aman untuk menangani data yang mungkin tidak ada.
        Optional<String> validAsset = Optional.ofNullable(supportedCryptos.contains(asset) ? asset : null);

        validAsset.ifPresentOrElse(a -> {
            double price = cryptoPrices.get(asset); // Mengambil harga terbaru
            CryptoTrade trade = new CryptoTrade(user, asset, amount, isBuyOrder, price);
            orderQueue.offer(trade); // 5. Menambahkan order ke antrean
            System.out.println("Order ditambahkan: " + trade);
        }, () -> System.out.println("Aset tidak didukung!"));
    }

    // Fungsi untuk membatalkan (dequeue) order sebelum diproses
    public void dequeueOrder(String user, String asset) {
        // 7. Menggunakan removeIf untuk menghapus order tertentu dari antrean
        //    Alasan: removeIf lebih efisien dibandingkan iterasi manual, sehingga menghindari kondisi balapan (race condition).
        boolean removed = orderQueue.removeIf(trade -> trade.user().equals(user) && trade.asset().equals(asset));
        if (removed) {
            System.out.println("Order untuk " + user + " dengan aset " + asset + " berhasil dihapus dari antrean.");
        } else {
            System.out.println("Order tidak ditemukan atau sudah diproses.");
        }
    }

    // Fungsi untuk memproses antrean order
    public void processOrders() {
        // 4. Menggunakan ExecutorService untuk memproses order secara paralel
        //    Alasan: Thread pool meningkatkan efisiensi eksekusi transaksi secara bersamaan tanpa harus membuat thread baru setiap kali.
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();

        while (!orderQueue.isEmpty()) {
            CryptoTrade trade = orderQueue.poll(); // 5. FIFO
            futures.add(executor.submit(() -> executeTrade(trade)));
        }

        // Menunggu semua task selesai
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Fungsi eksekusi transaksi
    private void executeTrade(CryptoTrade trade) {
        tradeHistory.add(trade); // 1. Menyimpan transaksi ke dalam list
        userAssets.computeIfAbsent(trade.user(), k -> ConcurrentHashMap.newKeySet()).add(trade.asset());
        System.out.println("Order dieksekusi: " + trade);
    }

    // Menampilkan riwayat transaksi
    public void printTradeHistory() {
        tradeHistory.forEach(System.out::println);
    }

    // Menampilkan daftar aset pengguna
    public void printUserAssets(String user) {
        System.out.println("Aset milik " + user + ": " + userAssets.getOrDefault(user, Set.of()));
    }

    // Menampilkan harga kripto saat ini
    public void printCryptoPrices() {
        System.out.println("Harga Kripto Saat Ini: " + cryptoPrices);
    }

    public static void main(String[] args) {
        CryptoTradingSystem system = new CryptoTradingSystem();

        system.placeOrder("Alice", "BTC", 0.5, true);
        system.placeOrder("Bob", "ETH", 2.0, true);
        system.placeOrder("Charlie", "ADA", 50.0, true);
        system.placeOrder("Charlie", "XRP", 200.0, true);

        // Membatalkan salah satu order sebelum diproses
        system.dequeueOrder("Charlie", "XRP");

        system.processOrders();

        System.out.println("\nRiwayat Transaksi:");
        system.printTradeHistory();
        system.printUserAssets("Alice");
        system.printCryptoPrices();
    }
}