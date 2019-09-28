import junit.framework.TestCase;
import org.junit.Test;

public class ParserTest extends TestCase {

    @Test
    public void testParseSelfStat()
    {

        String input = "binder proc state:\n" +
                "    proc 20344\n" +
                "    context binder\n" +
                "  thread 20344: l 00 need_return 0 tr 0\n" +
                "  thread 20360: l 12 need_return 0 tr 0\n" +
                "  thread 20361: l 11 need_return 0 tr 0\n" +
                "  ref 1501370: desc 0 node 3 s 1 w 1 d 0000000000000000\n" +
                "  ref 1501384: desc 1 node 1025 s 1 w 0 d 0000000000000000\n" +
                "  buffer 1501383: 0000000000000000 size 24:8:0 delivered";
        assertEquals(Bindump.extractStatAndGetServiceNode(input), 1025);
    }

    @Test
    public void testParseStat()
    {
        String input = "binder proc state:\n" +
                "proc 995\n" +
                "context hwbinder\n" +
                "  thread 995: l 12 need_return 0 tr 0\n" +
                "  node 1047: u0000007c70e2d360 c0000007c70e530a0 pri 0:120 hs 1 hw 1 ls 0 lw 0 is 2 iw 2 tr 1 proc 1079 557\n" +
                "  ref 1045: desc 0 node 2 s 1 w 1 d 0000000000000000";

    }

    @Test
    public void testParseHolder()
    {

        String input = "proc 973\n" +
                "context hwbinder\n" +
                "  thread 1080: l 00 need_return 0 tr 0\n" +
                "  thread 1646: l 12 need_return 0 tr 0\n" +
                "  node 6416: u000000798e412100 c000000798e4191e0 pri 0:120 hs 1 hw 1 ls 0 lw 0 is 1 iw 1 tr 1 proc 978\n" +
                "  ref 1164: desc 0 node 2 s 1 w 1 d 0000000000000000\n" +
                "  ref 1171: desc 1 node 1029 s 1 w 1 d 0000000000000000\n" +
                "  buffer 6492226: 0000000000000000 size 88:8:16 delivered\n" +
                "binder proc state:\n" +
                "proc 973\n" +
                "context binder\n" +
                "  thread 973: l 12 need_return 0 tr 0\n" +
                "  thread 1080: l 00 need_return 0 tr 0\n" +
                "  thread 1085: l 12 need_return 0 tr 0\n" +
                "  thread 1646: l 00 need_return 0 tr 0\n" +
                "  node 1157: u000000798e82d3e0 c000000798e828518 pri 0:139 hs 1 hw 1 ls 0 lw 0 is 2 iw 2 tr 1 proc 1079 556\n" +
                "  node 1161: u000000798e82d400 c000000798e8284e8 pri 0:139 hs 1 hw 1 ls 0 lw 0 is 1 iw 1 tr 1 proc 556\n" +
                "  ref 1155: desc 0 node 3 s 1 w 1 d 0000000000000000\n" +
                "  ref 6407: desc 1 node 6140 s 2 w 2 d 0000000000000000\n" +
                "  buffer 6245996: 0000000000000000 size 8000:0:0 delivered\n" +
                "  buffer 6476735: 0000000000000000 size 9016:0:0 delivered";

        int ret = Bindump.procUserOrOwner(input, 1157);
        assertEquals(ret, ServiceUsage.FLAG_NODE_OWNER);
        ret = Bindump.procUserOrOwner(input, 6140);
        assertEquals(ret, ServiceUsage.FLAG_NODE_USER);
    }

}
