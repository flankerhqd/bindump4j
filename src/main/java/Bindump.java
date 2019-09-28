import android.app.ActivityThread;
import android.content.Context;
import android.os.IBinder;
import android.os.Looper;
import android.os.ServiceManager;
import android.util.Log;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class ServiceUsage{
    static final int FLAG_NODE_USER = 0x1;
    static final int FLAG_NODE_OWNER = 0x2;
    String path;
    int usage;
    int pid;

    public ServiceUsage(String path, int usage, int pid) {
        this.path = path;
        this.usage = usage;
        this.pid = pid;
    }

    boolean isServiceOwner()
    {
        return (usage & FLAG_NODE_OWNER) != 0;
    }
    @Override
    public String toString() {
        if( (usage & ServiceUsage.FLAG_NODE_OWNER) != 0)
        {
            return String.format("Owner: %d\t%s", pid, path);
        }
        else if ( (usage & ServiceUsage.FLAG_NODE_USER) != 0)
        {
            return String.format("User: %d\t%s", pid, path);
        }
        else{
            //this should not happen
            return "???";
        }
    }
}

public class Bindump {

    private static Context getContext()
    {
        Looper.prepare();
        Context ctx = null;
        try
        {
            Class atclz = Class.forName("android.app.ActivityThread");
            Method stmtd = atclz.getDeclaredMethod("systemMain");
            stmtd.setAccessible(true);


            ActivityThread thread = (ActivityThread) stmtd.invoke(null);

            Class ctclz = Class.forName("android.app.ContextImpl");
            Method cscmtd = ctclz.getDeclaredMethod("createSystemContext", atclz);
            cscmtd.setAccessible(true);
            ctx = (Context) cscmtd.invoke(null, thread);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        Log.e("bindump-java", "context is " + ctx);
        return ctx;
    }


    private static String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        File file = new File(path);
        FileInputStream stream = new FileInputStream(file);
        while(stream.read(buf) != -1)
        {
            sb.append(new String(buf));
        }
        stream.close();
        return sb.toString();
    }

        /*
    a70q:/ # cat /sys/kernel/debug/binder/proc/20344
    binder proc state:
    proc 20344
    context binder
  thread 20344: l 00 need_return 0 tr 0
  thread 20360: l 12 need_return 0 tr 0
  thread 20361: l 11 need_return 0 tr 0
  ref 1501370: desc 0 node 3 s 1 w 1 d 0000000000000000
  ref 1501384: desc 1 node 1025 s 1 w 0 d 0000000000000000
  buffer 1501383: 0000000000000000 size 24:8:0 delivered
     */

    public static int getSelfHoldingNode() throws IOException {
        int mypid = Integer.parseInt(new File("/proc/self").getCanonicalFile().getName());
        String binderstat = readFile(String.format("/sys/kernel/debug/binder/proc/%d", mypid));
        return extractStatAndGetServiceNode(binderstat);
    }

    protected static int extractStatAndGetServiceNode(String binderstat) {
        //find first "context binder"
        //first ref is usually service manager
        int svcMgrNodeIndex = binderstat.indexOf("context binder");
        if(svcMgrNodeIndex == -1)
        {
            throw new IllegalArgumentException("unreachable: the process does not have context binder");
        }
        svcMgrNodeIndex = binderstat.indexOf("node", svcMgrNodeIndex + 1);
        if(svcMgrNodeIndex == -1)
        {
            //wtf? cannot find any node?
            throw new IllegalArgumentException("cannot find any node in binder stat");
        }
        //next ref is the service we opened
        int svcNodeIndex = binderstat.indexOf("node", svcMgrNodeIndex + 1);
        Scanner scanner = new Scanner(binderstat.substring(svcNodeIndex + 1));
        scanner.next();
        return scanner.nextInt();
    }

    private static String getProcessNameByPid(int pid) throws IOException {
        File procExeFile = new File(String.format("/proc/%d/exe", pid));
        String exePath = procExeFile.toPath().toRealPath().toString();

        String cmdline = readFile(String.format("/proc/%d/cmdline", pid));
        //special handle for app_process
        if(exePath.contains("app_process"))
        {
            //use cmdline instead
            return cmdline;
        }
        return cmdline + "\t" + exePath;
    }

    //note: we only parse "context binder", hwbinder or vndbinder should be dropped
    static int procUserOrOwner(String binderstat, int nodeid)
    {
        int beginindex = binderstat.indexOf("context binder");
        int endindex = binderstat.indexOf("binder proc state", beginindex + 15);
        int result = 0;

        String symbol = String.format("node %d", nodeid);
        if(beginindex == -1)
        {
            //this process only holds one kind of binder, but not what we desired
        }
        else{
            if(endindex != -1)
            {
               binderstat = binderstat.substring(beginindex+1, endindex);
            }
            for(String line: binderstat.split("\n"))
            {
                line = line.trim();
                if(line.contains(symbol + " ") || line.contains(symbol + ":"))
                {
                    if(line.startsWith("ref "))
                    {
                        //this process uses this binder node

                        result |= ServiceUsage.FLAG_NODE_USER;
                    }
                    else if(line.startsWith("node "))
                    {
                        //this process holds this binder node

                        result |= ServiceUsage.FLAG_NODE_OWNER;
                    }
                    else {
                        //???wtf
                    }
                }
            }
        }
        return result;
    }

    /*
    //todo: shall we treat binder/hwbinder/vndbinder differently?
    //currently we only consider servicemanager binder
    // For users, we look for a line of the form
    //  ref 4: desc 0 node %d s 1 w 1 d  ...
    // For owners, we look for a line of the form
    // node %d: u002d70b0 c002d7704 ... blah blah... proc pid1 pid2 ...
     */
    private static List<ServiceUsage> iterateProcFs(int nodeid)
    {
        List<ServiceUsage> usageList = new ArrayList<>();
        File procroot = new File("/sys/kernel/debug/binder/proc/");
        for(File statFile: procroot.listFiles())
        {
            try {
                String binderstat = readFile(statFile.getPath());
                int pid = Integer.parseInt(statFile.getName());
                int ret = procUserOrOwner(binderstat, nodeid);
                if(ret != 0)
                {
                    String procinfo = getProcessNameByPid(pid);
                    usageList.add(new ServiceUsage(procinfo, ret, pid));
                }
            } catch (IOException e) {
                //e.printStackTrace();
                e.printStackTrace();
                //this pid may have died while we iterate. ignore exception
            }
        }
        return usageList;
    }

    public static void main(String[] args)  {

        if(args.length == 2 && args[0].equals("dump"))
        {
            List<ServiceUsage> usages = getServiceUsage(args[1]);
            for(ServiceUsage usage: usages)
            {
                System.out.println(usage.toString());
            }
        }
        else if (args.length == 1 && args[0].equals("dumpall"))
        {
            String[] services = getServices();
            if(services == null)
            {
                System.err.println("we cannot get all services. see exception trace for details");
            }
            else{
                for(String service: services)
                {
                    //problem: a naive but workable way to revisit service.
                    //Question: why don't just call getservice function? because the service handle will remain in client
                    //until JVM GCed, whose timing we cannot control. Thus affecting our linking process
                    try {
                        String output = execCommand(new String[]{"app_process", "/data/local/tmp/", "Bindump", "dump", service});
                        for(String line: output.split("\n"))
                        {
                            if(line.contains("Owner:"))
                            {
                                System.out.println(line + " for service :" + service);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if(args.length != 2)
        {
            System.out.println("Usage: <bindump> dump <service> : used to dump a single service");
            System.out.println("Usage: <bindump> dumpall: used to dump all services reachable");
        }
    }

    private static String execCommand(String[] commands) throws IOException {
        Runtime rt = Runtime.getRuntime();
        Process proc = rt.exec(commands);

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(proc.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(proc.getErrorStream()));

        String s= null;
        StringBuilder sb = new StringBuilder();
        while ((s = stdInput.readLine()) != null) {
            sb.append(s + "\n");
        }
        stdInput.close();
        stdError.close();
        proc.destroy();
        return sb.toString();
    }


    private static String[] getServices() {

        try {
            Class svcmgrClz = Class.forName("android.os.ServiceManager");
            Method mtd = svcmgrClz.getDeclaredMethod("listServices");
            mtd.setAccessible(true);
            return (String[]) mtd.invoke(null);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static List<ServiceUsage> getServiceUsage(String arg) {
        IBinder binder = null;
        System.out.println("trying " + arg + " ...");
        try {
            binder = ServiceManager.getServiceOrThrow(arg);
            //System.out.println( "service got is " + binder);
            try {
                int nodeid = getSelfHoldingNode();
                return iterateProcFs(nodeid);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("we did not find this service.");
        }
        return new ArrayList<>();
    }
}
