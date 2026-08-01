import java.net.*;

public class SockDiag {
    static void t(String label, Runnable r) {
        try { r.run(); System.out.println("OK    " + label); }
        catch (Throwable e) { System.out.println("FAIL  " + label + "  -> " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    static void bind(String host) throws Exception {
        try (ServerSocket s = new ServerSocket()) {
            s.bind(host == null ? new InetSocketAddress(0) : new InetSocketAddress(InetAddress.getByName(host), 0));
        }
    }

    public static void main(String[] args) {
        System.out.println("preferIPv4Stack=" + System.getProperty("java.net.preferIPv4Stack")
                + " preferIPv6Addresses=" + System.getProperty("java.net.preferIPv6Addresses"));
        t("bind wildcard (new InetSocketAddress(0))", () -> { try { bind(null); } catch (Exception e) { throw new RuntimeException(e); } });
        t("bind 127.0.0.1", () -> { try { bind("127.0.0.1"); } catch (Exception e) { throw new RuntimeException(e); } });
        t("bind ::1", () -> { try { bind("::1"); } catch (Exception e) { throw new RuntimeException(e); } });
        t("bind 0.0.0.0", () -> { try { bind("0.0.0.0"); } catch (Exception e) { throw new RuntimeException(e); } });
        t("bind ::", () -> { try { bind("::"); } catch (Exception e) { throw new RuntimeException(e); } });
        t("DatagramSocket()", () -> { try (DatagramSocket d = new DatagramSocket()) { } catch (Exception e) { throw new RuntimeException(e); } });
        t("DNS resolve github.com", () -> { try { InetAddress.getByName("github.com"); } catch (Exception e) { throw new RuntimeException(e); } });
        t("loopback connect ::1", () -> {
            try (ServerSocket s = new ServerSocket()) {
                s.bind(new InetSocketAddress(InetAddress.getByName("::1"), 0));
                try (Socket c = new Socket()) { c.connect(new InetSocketAddress("::1", s.getLocalPort()), 3000); }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
}
