package pkg;

import nano.http.d2.console.Console;
import nano.http.d2.console.Logger;
import nano.http.d2.json.NanoJSON;
import nano.http.d2.utils.Encoding;
import nano.http.d2.utils.WebSocketClient;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Main {
    private static final double THRESHOLD = 0.001;

    private static boolean equal(double a, double b) {
        return Math.abs(a - b) < THRESHOLD;
    }

    private static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(out);
            gzip.write(data);
            gzip.close();
            return out.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] ungzip(byte[] data) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            in.read();
            GZIPInputStream gzip = new GZIPInputStream(in);
            byte[] buffer = new byte[1024];
            int n;
            while ((n = gzip.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] compress(String data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] gzip = gzip(bytes);
            out.write(0x01);
            out.write(gzip);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    private static String decompress(byte[] data) {
        if (data[0] == 0x01) {
            return new String(ungzip(data));
        } else {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    public static void main(String[] args) {
        Logger.info("Fanho 丨 繁花量化 v1.0");
        Logger.info("> 系统启动中...");
        Logger.info("> 登录到平台 [iirose] 服务器 [m1]");
        Logger.info("> 请键入用户名");
        String username = Console.await();
        Logger.info("> 请键入密码");
        String password = Console.await();

        NanoJSON json = new NanoJSON();
        json.put("r", "5ce6a4b520a90");
        json.put("n", username);
        json.put("p", Encoding.enMd5(password).toLowerCase());
        json.put("st", "n");
        json.put("mo", "");
        json.put("mb", "");
        json.put("mu", "01");
        json.put("nt", "!12");
        json.put("vc", "1132");
        json.put("fp", "@" + Encoding.enMd5(username).toLowerCase());

        WebSocketClient client;
        try {
            client = new WebSocketClient("wss://m1.iirose.com:8778/");
            client.sendBinary(compress("*" + json));
        } catch (Exception e) {
            Logger.error("> 无法连接到服务器...网络环境异常？");
            return;
        }

        NanoJSON a = new NanoJSON();
        a.put("m", "繁花量化套件上线成功 https://github.com/huzpsb/Fanho");
        a.put("mc", "ff0000");
        a.put("i", "0");
        client.sendBinary(compress(a.toString()));

        Logger.info("> 连接服务器成功");
        Thread t = new Thread(() -> {
            Random random = new Random();
            try {
                Thread.sleep(5000 + random.nextInt(5000));
                client.sendBinary(compress("s"));
                client.sendBinary(compress(">#"));
            } catch (Exception ignored) {
            }
        });
        t.setName("Heartbeat");
        t.setDaemon(true);
        t.start();

        ScriptEngine scriptEngine = (new ScriptEngineManager()).getEngineByName("JavaScript");
        Bindings context = scriptEngine.createBindings();
        context.put("console", new Jso());
        Logger.info("> 开始加载量化策略文件...");
        try {
            Scanner scanner = new Scanner(new File("fanho.js"), "UTF-8");
            StringBuilder sb = new StringBuilder();
            while (scanner.hasNextLine()) {
                sb.append(scanner.nextLine()).append("\n");
            }
            scanner.close();
            scriptEngine.eval(sb.toString().replace("const ", "let "), context);
        } catch (Exception e) {
            Logger.error("> 无法加载量化策略文件...请检查量化策略是否正确？", e);
            return;
        }

        double lastPrice = -1;
        while (true) {
            String msg = decompress(client.readBinary());
            if (msg.startsWith("%*\"") && msg.length() < 5) {
                Logger.error("> 服务器关闭了连接...用户名或密码错误？");
                return;
            }

            if (msg.startsWith(">")) {
                String[] parts = msg.substring(1).split("\"");
                if (parts.length != 5) {
                    return;
                }

                double sto = Double.parseDouble(parts[1]);
                int part = Integer.parseInt(parts[0]);
                double price = sto / part;
                int myHold = Integer.parseInt(parts[3]);
                double myCash = Double.parseDouble(parts[4]);


                if (!equal(price, lastPrice)) {
                    Logger.info("> 收到价格变动通知：价格" + String.format("%.3f", price) +
                            "钞/股 当前持仓" + myHold + "股票 当前资金" + String.format("%.2f", myCash) + "钞");
                    if (lastPrice != -1) {
                        Logger.info("> 价格变动，触发量化流程！");
                        int i;
                        try {
                            i = Integer.parseInt(scriptEngine.eval("quant(" + price + "," + myHold + "," + myCash + ");", context).toString());
                        } catch (Exception e) {
                            Logger.error("> 量化策略运行时出现异常，程序终止！", e);
                            return;
                        }
                        if (i != 0) {
                            int i2 = myHold + i;
                            Logger.info("> 量化策略决策为：" + myHold + " -> " + (i2));
                            if (i2 < 0 || price * i > myCash) {
                                Logger.error("> 量化策略决策不合理，程序终止！");
                                return;
                            }
                            if (i > 0) {
                                client.sendBinary(compress(">$" + i));
                            } else {
                                client.sendBinary(compress(">@" + -i));
                            }
                        } else {
                            Logger.info("> 量化策略决策为：不操作");
                        }
                        Logger.info("> 量化流程完成！");
                    } else {
                        Logger.info("> 价格初始化流程完成！");
                    }
                    lastPrice = price;
                }
            }
        }
    }
}
