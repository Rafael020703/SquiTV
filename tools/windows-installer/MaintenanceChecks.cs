using System;
using System.IO;
namespace SquiTV.Setup {
    internal static class MaintenanceChecks {
        private static int Main(string[] args) {
            try {
                if (args.Length != 1 || Directory.Exists(args[0])) throw new Exception("Use a NEW isolated fixture directory");
                string root = Path.GetFullPath(args[0]); Directory.CreateDirectory(root);
                foreach (string name in new string[] { "SquiTV", "SquiTVComponents", "SquiTVDebug", "OtherApp" }) {
                    string path=Path.Combine(root,name,"nested");Directory.CreateDirectory(path);
                    File.WriteAllText(Path.Combine(path,"fixture.txt"),"fixture");
                }
                Maintenance.RemoveOwned(root,"SquiTV");Maintenance.RemoveOwned(root,"SquiTVComponents");
                if (Directory.Exists(Path.Combine(root,"SquiTV")) || Directory.Exists(Path.Combine(root,"SquiTVComponents"))) throw new Exception("Owned roots remain");
                if (!File.Exists(Path.Combine(root,"SquiTVDebug","nested","fixture.txt"))) throw new Exception("Debug data was touched");
                bool rejected=false;
                try { Maintenance.RemoveOwned(root,"OtherApp"); } catch(InvalidOperationException){rejected=true;}
                if (!rejected || !File.Exists(Path.Combine(root,"OtherApp","nested","fixture.txt"))) throw new Exception("Unowned path accepted");
                Console.WriteLine("PASS: cleanup removes only owned fixture roots; debug/other files preserved; NO actual application data/registry touched");
                return 0;
            } catch(Exception error){Console.Error.WriteLine(error);return 1;}
        }
    }
}
