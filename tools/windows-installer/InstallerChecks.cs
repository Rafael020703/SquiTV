// Separate executable entry point, compiled only with debug installer resources. Never invokes MSI.
using System;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;

namespace SquiTV.Setup {
    internal static class InstallerChecks {
        private static int checks;
        private static void Check(bool value,string name) {
            if (!value) throw new Exception("FAILED: " + name);
            Console.WriteLine("OK: " + name); checks++;
        }
        [STAThread] private static int Main(string[] args) {
            try { Run(args).GetAwaiter().GetResult(); return 0; }
            catch (Exception error) { Console.Error.WriteLine(error); return 1; }
        }
        private static async Task Run(string[] args) {
            if (args.Length < 2) throw new Exception("Provide the pinned ZIP and a NEW isolated output directory");
            InstallerDefinition app = Program.ReadResource<InstallerDefinition>("squitv.installer.json");
            RuntimeDefinition runtime = Program.ReadResource<RuntimeDefinition>("squitv.runtime.json");
            if (!app.debug || app.name != "SquiTVDebug") throw new Exception("Checks MUST use DEBUG resources");
            Program.Validate(app,runtime);
            Check(new FileInfo(args[0]).Length == runtime.archiveSize && Program.Sha256(args[0]) == runtime.archiveSha256,"pinned official archive");
            string root = Path.GetFullPath(args[1]);
            if (Directory.Exists(root) || File.Exists(root)) throw new Exception("Refusing to replace an existing test directory");
            Directory.CreateDirectory(root);
            File.WriteAllText(Path.Combine(root,".squitv-installer-checks"),"DEBUG ONLY; MSI is never executed\n");
            string components = Path.Combine(root,"components"); Directory.CreateDirectory(components);
            string downloads = Path.Combine(components,"downloads"); Directory.CreateDirectory(downloads);
            string archive = Path.Combine(downloads,runtime.archiveSha256 + ".zip");
            File.Copy(args[0],archive);
            using (SetupForm form = new SetupForm(app,runtime)) {
                // No Application.Run in this console harness; do not capture WinForms' UI context.
                SynchronizationContext.SetSynchronizationContext(null);
                string target = Path.Combine(components,runtime.id);
                await form.EnsureRuntime(components,CancellationToken.None,Enumerable.Empty<string>());
                Check(form.ValidRuntime(target,CancellationToken.None),"first preparation and every-file SHA-256");
                File.Delete(archive);
                await form.EnsureRuntime(components,CancellationToken.None,Enumerable.Empty<string>());
                Check(!File.Exists(archive),"second preparation reuses cache without archive/network");
                string library = Path.Combine(target,"libvlc.dll");
                using (FileStream file = File.Open(library,FileMode.Open,FileAccess.ReadWrite)) {
                    int original = file.ReadByte(); file.Position = 0; file.WriteByte((byte)(original ^ 1));
                }
                Check(!form.ValidRuntime(target,CancellationToken.None),"same-size DLL corruption rejected");
                File.Copy(args[0],archive);
                await form.EnsureRuntime(components,CancellationToken.None,Enumerable.Empty<string>());
                Check(form.ValidRuntime(target,CancellationToken.None),"automatic repair from verified archive");
                Check(Directory.GetDirectories(components,runtime.id + ".previous-*").Length == 1,"previous runtime preserved");
                File.WriteAllText(Path.Combine(target,"unexpected.dll"),"fixture");
                Check(!form.ValidRuntime(target,CancellationToken.None),"unexpected plugin/DLL rejected");
                File.Delete(Path.Combine(target,"unexpected.dll"));
                string migration = Path.Combine(root,"migration"); Directory.CreateDirectory(migration);
                await form.EnsureRuntime(migration,CancellationToken.None,new string[] { target });
                Check(form.ValidRuntime(Path.Combine(migration,runtime.id),CancellationToken.None) &&
                    !Directory.Exists(Path.Combine(migration,"downloads")),"verified previous installation migrated without download");
                Check(form.ValidRuntime(target,CancellationToken.None),"migration does not alter previous libraries");
                string msi = form.PrepareMsi(components,CancellationToken.None);
                Check(new FileInfo(msi).Length == app.msiSize && Program.Sha256(msi) == app.msiSha256,"embedded application MSI verified, NOT executed");
                Check(Program.Sha256(form.PrepareUserTransform(components,CancellationToken.None)) == app.mstSha256,"legacy per-user transform verified");
                File.WriteAllText(msi,"corrupt");
                Check(Program.Sha256(form.PrepareMsi(components,CancellationToken.None)) == app.msiSha256,"corrupt MSI cache repaired automatically");
                using (CancellationTokenSource cancel = new CancellationTokenSource()) {
                    cancel.Cancel(); bool cancelled = false;
                    try { await form.EnsureRuntime(components,cancel.Token,Enumerable.Empty<string>()); }
                    catch (OperationCanceledException) { cancelled = true; }
                    Check(cancelled,"cancellation propagated");
                }
                foreach (string path in new string[] { "../escape.dll", "/absolute.dll", "C:/file.dll", "plugins/../../escape.dll", "a\\b.dll" })
                    Check(!Program.SafeRelative(path),"path rejected: " + path);
                RuntimeDefinition bad = Program.ReadResource<RuntimeDefinition>("squitv.runtime.json");
                bad.architecture = "arm64"; bool rejected = false;
                try { Program.Validate(app,bad); } catch (InvalidDataException) { rejected = true; }
                Check(rejected,"wrong architecture rejected");
                string truncated = Path.Combine(root,"truncated.zip"); File.WriteAllText(truncated,"truncated");
                rejected = false;
                try { form.Extract(truncated,Path.Combine(root,"unused"),CancellationToken.None); }
                catch (InvalidDataException) { rejected = true; }
                Check(rejected,"truncated archive rejected");
                if (args.Length == 3 && args[2] == "--network") {
                    string network = Path.Combine(root,"official-network.zip");
                    await form.Download(network,CancellationToken.None);
                    Check(Program.Sha256(network) == runtime.archiveSha256,"real HTTPS download and SHA-256");
                }
            }
            Console.WriteLine("PASS: " + checks + " checks; DEBUG only; no MSI/application executed; output " + root);
        }
    }
}
