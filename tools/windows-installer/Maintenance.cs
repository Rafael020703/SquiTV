using System;
using System.Collections.Generic;
using System.IO;
using Microsoft.Win32;

[assembly: System.Runtime.Versioning.TargetFramework(".NETFramework,Version=v4.8")]
namespace SquiTV.Setup {
    // Runs only from normal MSI REMOVE=ALL, never from an upgrade. MSI owns application files,
    // shortcuts and registration. This helper removes only the exact application data roots.
    internal static class Maintenance {
        private static int Main(string[] args) {
            try {
                if (args.Length != 2 || (args[0] != "user" && args[0] != "machine") ||
                    (args[1] != "SquiTV" && args[1] != "SquiTVDebug")) return 1;
                string name = args[1];
                if (args[0] == "user") {
                    string local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                    RemoveOwned(local,name); RemoveOwned(local,name + "Components");
                    RemoveRegistration(RegistryHive.CurrentUser,name);
                } else {
                    RemoveOwned(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),name + "Components");
                    using (RegistryKey profiles = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProfileList")) {
                        foreach (string sid in profiles.GetSubKeyNames()) {
                            if (!sid.StartsWith("S-1-5-21-",StringComparison.Ordinal) && !sid.StartsWith("S-1-12-1-",StringComparison.Ordinal)) continue;
                            using (RegistryKey profile = profiles.OpenSubKey(sid)) {
                                string home = profile.GetValue("ProfileImagePath") as string;
                                if (String.IsNullOrEmpty(home)) continue;
                                home = Environment.ExpandEnvironmentVariables(home);
                                string local = Path.Combine(home,"AppData","Local");
                                RemoveOwned(local,name); RemoveOwned(local,name + "Components");
                            }
                        }
                    }
                    RemoveRegistration(RegistryHive.LocalMachine,name);
                }
                return 0;
            } catch { return 1; }
        }
        private static void RemoveRegistration(RegistryHive hive,string name) {
            foreach (RegistryView view in new RegistryView[] { RegistryView.Registry64,RegistryView.Registry32 }) {
                using (RegistryKey root = RegistryKey.OpenBaseKey(hive,view)) {
                    using (RegistryKey brand = root.OpenSubKey(@"Software\Squi TV",true)) {
                        if (brand == null) continue;
                        brand.DeleteSubKeyTree(name,false);
                    }
                    using (RegistryKey brand = root.OpenSubKey(@"Software\Squi TV")) {
                        if (brand == null || brand.SubKeyCount != 0 || brand.ValueCount != 0) continue;
                    }
                    root.DeleteSubKey(@"Software\Squi TV",false);
                }
            }
        }
        internal static void RemoveOwned(string parent,string folder) {
            if (folder != "SquiTV" && folder != "SquiTVComponents" && folder != "SquiTVDebug" && folder != "SquiTVDebugComponents")
                throw new InvalidOperationException("Invalid application data root");
            string basePath = Path.GetFullPath(parent).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
            string path = Path.GetFullPath(Path.Combine(basePath,folder));
            if (!path.StartsWith(basePath,StringComparison.OrdinalIgnoreCase)) throw new InvalidOperationException();
            if (!Directory.Exists(path)) return;
            DeleteDirectory(path);
        }
        private static void DeleteDirectory(string directory) {
            // Never follow a junction into another application's/personal directory.
            if ((File.GetAttributes(directory) & FileAttributes.ReparsePoint) != 0) { Directory.Delete(directory); return; }
            foreach (string path in Directory.GetFileSystemEntries(directory)) {
                if (Directory.Exists(path)) DeleteDirectory(path);
                else {
                    if ((File.GetAttributes(path) & FileAttributes.ReparsePoint) == 0) File.SetAttributes(path,FileAttributes.Normal);
                    File.Delete(path);
                }
            }
            Directory.Delete(directory);
        }
    }
}
