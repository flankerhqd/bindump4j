Vendor binder services proved to be an interesting part of android devices nature. They usually remains close-source, but sometimes open attack surface for privilege escalation. Namely examples like SVE-2016-7114 (By @laginimaineb), CVE-2018-9143 and CVE-2018-9139 (By @flanker_hqd) and so on, which are all memory corruption vulnerabilities.

# Locating interesting binder service processes

Before Android N, all binder services were registered to `servicemanager`, and communicated with each other under `/dev/binder`. After Android N, binder domains are splitted to normal domain under `/dev/binder`, vendor domain under `/dev/vndbinder`, and hardware domain under `/dev/hwbinder`. Normal untrusted_app access is restricted to `/dev/binder`.

There're possibly more than 200 binder services on some vendor devices, how do we deduce which services may have interesting native code for possible memory corruption? Most importantly, where are these services running in?

J previously released a tool named [bindump](http://newandroidbook.com/tools/bindump.html), by reading binder node data in debugfs, the tool can iterate which process owns a service and which processes are using this service. However days have passed and android has involved pretty much, major problems including
- debugfs is no longer readable by normal process so you will need root to run
- Binder node now have context so you have to distinguish between different domain context
- `libbinder.so` linking symbols change over each version so one may not be able to reuse the binary and need to recompile the source on top of corresponding AOSP source branch for every major version

To solve problem 2 and 3, I rewrite the tool in Java and encapsulated it into a standalone jar, which is portable across all android platforms and friendly with Android N or later.
 
# Usage
The usage is fairly simple. I assume you've obtained the precompiled binary or make your own, specifically `Main.jar` and `run.sh`. Push them to `/data/local/tmp/` :

```bash
a70q:/data/local/tmp # ./run.sh dump com.samsung.android.bio.face.IFaceDaemon                                   
User: 28986    app_process    /data/local/tmp/Bindump com.samsung.android.bio.face.IFaceDaemon
Owner: 1074    /system/bin/faced    /system/bin/faced
User: 554    /system/bin/servicemanager    /system/bin/servicemanager
```

Or simply use `dumpall` to dump all services and find your target.

# Compilation
If you would like to craft your own binary, just refer to `compile.sh`. Note to replace the `dx` path.

# Limitations
On Windows CMD sometimes the console display messed up. Maybe because windows console cannot property handle `\t`

# Usage In Action
In a following post I'll describe how we analyze and fuzz vendor binder services to find various vulnerabilities, e.g. CVEs mentioned above.