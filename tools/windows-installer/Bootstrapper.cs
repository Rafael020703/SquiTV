// Windows 10/11 built-in .NET Framework. No Java/VLC/global package manager is required to run setup.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Security.Principal;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

[assembly: System.Runtime.Versioning.TargetFramework(".NETFramework,Version=v4.8", FrameworkDisplayName=".NET Framework 4.8")]

namespace SquiTV.Setup {
    public sealed class RuntimeFile { public string path; public long size; public string sha256; }
    public sealed class RuntimeDefinition {
        public string id, version, architecture, url, archiveSha256, archivePrefix;
        public long archiveSize;
        public RuntimeFile[] files;
    }
    public sealed class InstallerDefinition {
        public string version, name, msiSha256, upgradeUuid, mstSha256;
        public bool debug;
        public long msiSize, mstSize;
    }
    internal sealed class InstalledApplication { internal string Directory; internal bool Machine; }

    internal static class Program {
        internal const int LaunchAfterInstall = 20;
        [STAThread] private static int Main(string[] args) {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            if (!Environment.Is64BitOperatingSystem) {
                MessageBox.Show("O Squi TV requer Windows x64.", "Squi TV"); return 1;
            }
            try {
                InstallerDefinition definition = ReadResource<InstallerDefinition>("squitv.installer.json");
                RuntimeDefinition runtime = ReadResource<RuntimeDefinition>("squitv.runtime.json");
                Validate(definition, runtime);
                bool acquired;
                string sid = WindowsIdentity.GetCurrent().User.Value;
                bool forcedMachine = args.Length == 1 && (args[0] == "--machine" || args[0] == "--machine-no-launch");
                if (args.Length > 0 && !forcedMachine) throw new InvalidDataException();
                if (forcedMachine && !IsAdministrator()) throw new InvalidOperationException();
                string mutexName = forcedMachine ? "Global\\" + definition.name + ".Setup.Machine" : "Local\\" + definition.name + ".Setup." + sid;
                using (Mutex gate = new Mutex(true, mutexName, out acquired)) {
                    if (!acquired) { MessageBox.Show("Já existe uma instalação desta versão em andamento.", "Squi TV"); return 1; }
                    try {
                        using (SetupForm setup = new SetupForm(definition, runtime, forcedMachine,
                                !forcedMachine || args[0] != "--machine-no-launch")) {
                            Application.Run(setup);
                            if (forcedMachine && setup.LaunchRequested) return LaunchAfterInstall;
                        }
                    }
                    finally { gate.ReleaseMutex(); }
                }
                return 0;
            } catch (Exception) {
                MessageBox.Show("Não foi possível preparar este instalador. Baixe novamente o EXE oficial.", "Squi TV");
                return 1;
            }
        }

        internal static T ReadResource<T>(string name) {
            using (Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(name)) {
                if (stream == null) throw new InvalidDataException("Recurso ausente.");
                using (StreamReader reader = new StreamReader(stream, Encoding.UTF8))
                    return new JavaScriptSerializer().Deserialize<T>(reader.ReadToEnd());
            }
        }

        private static bool Hash(string value) {
            return value != null && System.Text.RegularExpressions.Regex.IsMatch(value, "\\A[a-f0-9]{64}\\z");
        }
        internal static void Validate(InstallerDefinition app, RuntimeDefinition runtime) {
            if (app.name != (app.debug ? "SquiTVDebug" : "SquiTV") || !Hash(app.msiSha256) || app.msiSize <= 0 ||
                !Hash(app.mstSha256) || app.mstSize <= 0 || app.mstSize > 1024 * 1024 ||
                app.upgradeUuid != (app.debug ? "b473bd19-d856-4902-8aba-8a281b202ac8" : "cf4fe9a9-552b-43ed-8ca3-9e226f623362") ||
                !System.Text.RegularExpressions.Regex.IsMatch(app.version, "\\A[0-9]+\\.[0-9]+\\.[0-9]+\\z"))
                throw new InvalidDataException();
            Uri url = new Uri(runtime.url);
            if (url.Scheme != "https" || url.Host != "download.videolan.org" || url.UserInfo != "" ||
                runtime.architecture != "x64" || !Hash(runtime.archiveSha256) || runtime.archiveSize <= 0 ||
                runtime.archiveSize > 256L * 1024 * 1024 || runtime.files == null || runtime.files.Length > 4096 ||
                runtime.id != "vlc-" + runtime.version + "-x64-" + runtime.archiveSha256.Substring(0,16) ||
                !System.Text.RegularExpressions.Regex.IsMatch(runtime.id, "\\Avlc-[0-9]+\\.[0-9]+\\.[0-9]+-x64-[a-f0-9]{16}\\z") ||
                runtime.archivePrefix != "vlc-" + runtime.version + "/") throw new InvalidDataException();
            HashSet<string> paths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            long total = 0;
            foreach (RuntimeFile file in runtime.files) {
                if (!SafeRelative(file.path) || !paths.Add(file.path) || file.size < 0 || !Hash(file.sha256)) throw new InvalidDataException();
                total = checked(total + file.size);
            }
            if (total > 512L * 1024 * 1024 || !paths.Contains("libvlc.dll") || !paths.Contains("libvlccore.dll") ||
                !paths.Contains("COPYING.txt") || !paths.Contains("SQUI-TV-RUNTIME.txt")) throw new InvalidDataException();
        }
        internal static bool SafeRelative(string path) {
            return !String.IsNullOrEmpty(path) && !path.StartsWith("/") && !path.Contains("\\") &&
                path.Split('/').All(part => part != "" && part != "." && part != ".." &&
                    part.IndexOfAny(Path.GetInvalidFileNameChars()) < 0 && !part.EndsWith(".") && !part.EndsWith(" "));
        }
        internal static string Sha256(string path) {
            using (SHA256 hash = SHA256.Create()) using (Stream stream = File.OpenRead(path))
                return BitConverter.ToString(hash.ComputeHash(stream)).Replace("-", "").ToLowerInvariant();
        }
        internal static void NoLink(string path) {
            if ((File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0)
                throw new IOException("O diretório dos componentes não pode ser um link.");
        }
        internal static string Child(string root, string relative) {
            if (!SafeRelative(relative)) throw new InvalidDataException();
            string prefix = Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
            string result = Path.GetFullPath(Path.Combine(root, relative.Replace('/', Path.DirectorySeparatorChar)));
            if (!result.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException();
            return result;
        }
        [DllImport("msi.dll",CharSet=CharSet.Unicode)]
        private static extern uint MsiEnumRelatedProducts(string upgradeCode,uint reserved,uint index,StringBuilder productCode);
        [DllImport("msi.dll",CharSet=CharSet.Unicode)]
        private static extern uint MsiGetProductInfo(string product,string property,StringBuilder value,ref uint size);
        [DllImport("msi.dll",CharSet=CharSet.Unicode)]
        private static extern uint MsiOpenDatabase(string path,IntPtr mode,out uint database);
        [DllImport("msi.dll",CharSet=CharSet.Unicode)]
        private static extern uint MsiDatabaseOpenView(uint database,string query,out uint view);
        [DllImport("msi.dll")] private static extern uint MsiViewExecute(uint view,uint record);
        [DllImport("msi.dll")] private static extern uint MsiViewFetch(uint view,out uint record);
        [DllImport("msi.dll",CharSet=CharSet.Unicode)]
        private static extern uint MsiRecordGetString(uint record,uint field,StringBuilder value,ref uint size);
        [DllImport("msi.dll",CharSet=CharSet.Unicode)]
        private static extern int MsiGetComponentPath(string product,string component,StringBuilder path,ref uint length);
        [DllImport("msi.dll")] private static extern uint MsiCloseHandle(uint handle);

        private static string RecordString(uint record,uint field) {
            StringBuilder value = new StringBuilder(4096); uint size = 4095;
            return MsiRecordGetString(record,field,value,ref size) == 0 ? value.ToString() : null;
        }
        // jpackage's old MSI does not necessarily register InstallLocation. Resolve its actual
        // VLC keypath through the installed product's cached MSI/component registration instead.
        private static string RegisteredFileDirectory(string product, string fileName) {
            StringBuilder package = new StringBuilder(32768); uint length = 32767;
            if (MsiGetProductInfo(product,"LocalPackage",package,ref length) != 0) return null;
            uint database;
            if (MsiOpenDatabase(package.ToString(),IntPtr.Zero,out database) != 0) return null;
            try {
                string component = null; uint view;
                if (MsiDatabaseOpenView(database,"SELECT `Component_`, `FileName` FROM `File`",out view) != 0) return null;
                try {
                    if (MsiViewExecute(view,0) != 0) return null;
                    uint record;
                    while (MsiViewFetch(view,out record) == 0) {
                        try {
                            string name = RecordString(record,2);
                            if (name != null && name.Split('|').Last().Equals(fileName,StringComparison.OrdinalIgnoreCase))
                                component = RecordString(record,1);
                        } finally { MsiCloseHandle(record); }
                        if (component != null) break;
                    }
                } finally { MsiCloseHandle(view); }
                if (component == null || MsiDatabaseOpenView(database,"SELECT `Component`, `ComponentId` FROM `Component`",out view) != 0) return null;
                try {
                    if (MsiViewExecute(view,0) != 0) return null;
                    uint record;
                    while (MsiViewFetch(view,out record) == 0) {
                        try {
                            if (RecordString(record,1) != component) continue;
                            StringBuilder path = new StringBuilder(32768); length = 32767;
                            if (MsiGetComponentPath(product,RecordString(record,2),path,ref length) == 3)
                                return Path.GetDirectoryName(path.ToString());
                        } finally { MsiCloseHandle(record); }
                    }
                } finally { MsiCloseHandle(view); }
                return null;
            } finally { MsiCloseHandle(database); }
        }
        internal static IEnumerable<string> InstalledRuntimes(string upgradeUuid) {
            for (uint index = 0; index < 32; index++) {
                StringBuilder product = new StringBuilder(39);
                if (MsiEnumRelatedProducts("{" + upgradeUuid + "}",0,index,product) != 0) yield break;
                StringBuilder location = new StringBuilder(32768); uint length = 32767;
                if (MsiGetProductInfo(product.ToString(),"InstallLocation",location,ref length) == 0 && location.Length > 0)
                    yield return Path.Combine(location.ToString(),"app","resources","vlc");
                else {
                    string path = RegisteredFileDirectory(product.ToString(),"libvlc.dll");
                    if (path != null) yield return path;
                }
            }
        }
        [DllImport("msi.dll",CharSet=CharSet.Unicode)]
        private static extern uint MsiGetProductInfoEx(string product,string sid,uint context,string property,StringBuilder value,ref uint length);
        internal static InstalledApplication FindInstallation(InstallerDefinition app,bool machineOnly) {
            InstalledApplication user = null;
            for (uint index = 0; index < 32; index++) {
                StringBuilder code = new StringBuilder(39);
                if (MsiEnumRelatedProducts("{" + app.upgradeUuid + "}",0,index,code) != 0) break;
                StringBuilder version = new StringBuilder(128); uint count = 127;
                bool machine = MsiGetProductInfoEx(code.ToString(),null,4,"VersionString",version,ref count) == 0;
                if (machineOnly && !machine) continue;
                StringBuilder location = new StringBuilder(32768); count = 32767;
                string directory = MsiGetProductInfo(code.ToString(),"InstallLocation",location,ref count) == 0 && location.Length > 0
                    ? location.ToString() : RegisteredFileDirectory(code.ToString(),app.name + ".exe");
                if (String.IsNullOrEmpty(directory)) throw new IOException("Não foi possível localizar a pasta da instalação anterior. A instalação atual foi preservada.");
                directory = Path.GetFullPath(directory);
                if (directory.Contains("\"") || !System.IO.Directory.Exists(directory)) throw new IOException("A pasta da instalação anterior não está disponível.");
                InstalledApplication found = new InstalledApplication { Directory = directory, Machine = machine };
                if (machine) return found;
                user = found;
            }
            return user;
        }
        internal static bool IsAdministrator() {
            return new WindowsPrincipal(WindowsIdentity.GetCurrent()).IsInRole(WindowsBuiltInRole.Administrator);
        }

        internal static void StartInstalledApplication(InstallerDefinition app) {
            // The original, unelevated setup process launches the app after its UAC child exits.
            // Never carry the installer's administrator token into the media application.
            if (IsAdministrator()) throw new InvalidOperationException("Abra o Squi TV pelo menu Iniciar.");
            InstalledApplication installed = FindInstallation(app, false);
            if (installed == null) throw new IOException("A instalação não foi localizada no Windows.");
            string executable = Path.Combine(installed.Directory, app.name + ".exe");
            NoLink(installed.Directory); NoLink(executable);
            if (!File.Exists(executable)) throw new IOException("O executável instalado não foi localizado.");
            Process.Start(new ProcessStartInfo {
                FileName = executable, WorkingDirectory = installed.Directory, UseShellExecute = false
            });
        }
    }

    internal sealed class SetupForm : Form {
        private readonly InstallerDefinition app;
        private readonly RuntimeDefinition runtime;
        private readonly Label message = new Label();
        private readonly ProgressBar progress = new ProgressBar();
        private readonly Button install = new Button();
        private readonly Button cancel = new Button();
        private readonly CheckBox launchAfterInstall = new CheckBox();
        internal bool LaunchRequested { get; private set; }
        private CancellationTokenSource cancellation;
        private bool busy, installing, finished;
        private readonly bool forcedMachine;

        internal SetupForm(InstallerDefinition app, RuntimeDefinition runtime, bool forcedMachine = false,
                bool canRequestLaunch = true) {
            this.app = app; this.runtime = runtime;
            this.forcedMachine = forcedMachine;
            Text = app.debug ? "Squi TV Debug — Instalação" : "Squi TV — Instalação";
            Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            ClientSize = new Size(560,278); FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false; StartPosition = FormStartPosition.CenterScreen;
            BackColor = Color.FromArgb(12,18,29); ForeColor = Color.White; Font = new Font("Segoe UI",10);
            Label title = new Label { Text = app.debug ? "SQUI TV DEBUG" : "SQUI TV", AutoSize = true,
                Font = new Font("Segoe UI",18,FontStyle.Bold), ForeColor = Color.FromArgb(0,215,240), Left = 24, Top = 20 };
            message.SetBounds(24,72,512,64);
            message.Text = "O instalador prepara automaticamente os componentes necessários. Bibliotecas já verificadas serão reutilizadas.";
            progress.SetBounds(24,145,512,16);
            launchAfterInstall.Text = "Abrir o Squi TV ao concluir";
            launchAfterInstall.SetBounds(24,181,512,28);
            launchAfterInstall.Visible = false;
            launchAfterInstall.Enabled = canRequestLaunch && (forcedMachine || !Program.IsAdministrator());
            launchAfterInstall.Checked = launchAfterInstall.Enabled;
            install.Text = "Instalar / atualizar"; install.SetBounds(324,225,212,32);
            cancel.Text = "Cancelar"; cancel.SetBounds(200,225,112,32);
            install.Click += async delegate {
                if (!finished) { await InstallAsync(); return; }
                if (launchAfterInstall.Visible && launchAfterInstall.Checked) {
                    if (forcedMachine) LaunchRequested = true;
                    else {
                        try { Program.StartInstalledApplication(app); }
                        catch (Exception) {
                            launchAfterInstall.Checked = false;
                            Status("Instalação concluída. Não foi possível abrir automaticamente; use o menu Iniciar.",100);
                            return;
                        }
                    }
                }
                Close();
            };
            cancel.Click += delegate { if (busy) cancellation.Cancel(); else Close(); };
            Controls.AddRange(new Control[] { title,message,progress,launchAfterInstall,install,cancel });
            AcceptButton = install; CancelButton = cancel;
            if (forcedMachine) Shown += async delegate { await InstallAsync(); };
            FormClosing += delegate(object sender, FormClosingEventArgs e) {
                if (!busy) return;
                e.Cancel = true;
                if (!installing) cancellation.Cancel();
                else message.Text = "Aguarde a conclusão ou cancele na janela do Windows Installer.";
            };
        }

        private void Status(string text, int percent) {
            if (IsDisposed) return;
            if (InvokeRequired) { BeginInvoke(new Action<string,int>(Status),text,percent); return; }
            message.Text = text; progress.Value = Math.Max(0,Math.Min(100,percent));
        }

        private async Task InstallAsync() {
            busy = true; install.Enabled = false;
            cancellation = new CancellationTokenSource();
            try {
                CancellationToken token = cancellation.Token;
                InstalledApplication previous = Program.FindInstallation(app,forcedMachine);
                bool machine = forcedMachine || previous == null || previous.Machine;
                if (machine && !forcedMachine) {
                    // Let Windows request elevation once. No credentials or arbitrary commands
                    // cross the boundary; the child only accepts this fixed, validated mode.
                    try {
                        using (Process child = Process.Start(new ProcessStartInfo { FileName = Application.ExecutablePath,
                            Arguments = Program.IsAdministrator() ? "--machine-no-launch" : "--machine",
                            UseShellExecute = true, Verb = "runas" })) {
                            if (child == null) throw new IOException("Não foi possível iniciar a instalação.");
                            Hide();
                            await Task.Run(delegate { child.WaitForExit(); });
                            if (child.ExitCode == Program.LaunchAfterInstall) {
                                try { Program.StartInstalledApplication(app); }
                                catch (Exception) { MessageBox.Show("Instalação concluída. Abra o Squi TV pelo menu Iniciar.", "Squi TV"); }
                            }
                        }
                        busy = false; Close(); return;
                    } catch (System.ComponentModel.Win32Exception error) {
                        if (error.NativeErrorCode == 1223) throw new OperationCanceledException();
                        throw;
                    }
                }
                string root = Path.Combine(Environment.GetFolderPath(machine ? Environment.SpecialFolder.CommonApplicationData :
                    Environment.SpecialFolder.LocalApplicationData), app.name + "Components");
                Directory.CreateDirectory(root); Program.NoLink(root);
                await Task.Run(async delegate { await EnsureRuntime(root,token); },token);
                token.ThrowIfCancellationRequested();
                Status("Componentes prontos. Preparando a instalação do aplicativo…",100);
                string msi = await Task.Run(() => PrepareMsi(root,token), token);
                string transform = machine ? null : await Task.Run(() => PrepareUserTransform(root,token), token);
                token.ThrowIfCancellationRequested();
                installing = true; cancel.Enabled = false;
                int result = await Task.Run(delegate {
                    using (Process process = Process.Start(new ProcessStartInfo {
                        FileName = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),"msiexec.exe"),
                        Arguments = "/i \"" + msi + "\" /norestart" + (transform == null ? "" : " TRANSFORMS=\"" + transform + "\" MSITRANSFORMSSECURE=1") +
                            (previous == null ? "" : " INSTALLDIR=\"" + previous.Directory.TrimEnd('\\') + "\" /passive"),
                        UseShellExecute = false })) {
                        process.WaitForExit(); return process.ExitCode;
                    }
                });
                if (result == 1602) throw new OperationCanceledException();
                if (result != 0 && result != 3010) throw new IOException("O Windows Installer não concluiu a instalação (" + result + "). Tente novamente.");
                finished = true; install.Text = "Concluir"; cancel.Visible = false;
                launchAfterInstall.Visible = result == 0 && launchAfterInstall.Enabled;
                Status(result == 3010 ? "Instalação concluída. Reinicie o Windows quando for conveniente." : "Instalação concluída. O Squi TV está disponível no menu Iniciar.",100);
            } catch (OperationCanceledException) {
                if (!Visible && !IsDisposed) Show();
                Status("Instalação cancelada. Você pode tentar novamente.",0);
            }
            catch (Exception error) {
                if (!Visible && !IsDisposed) Show();
                Status("Não foi possível concluir. " + error.Message,0);
            }
            finally { busy = false; installing = false; install.Enabled = true; cancel.Enabled = true; cancellation.Dispose(); }
        }

        internal bool ValidRuntime(string directory, CancellationToken token) {
            if (!Directory.Exists(directory)) return false;
            try {
                Program.NoLink(directory);
                HashSet<string> expected = new HashSet<string>(runtime.files.Select(file => file.path),StringComparer.OrdinalIgnoreCase);
                Queue<string> pending = new Queue<string>(); pending.Enqueue(directory);
                int seen = 0;
                while (pending.Count > 0) {
                    token.ThrowIfCancellationRequested();
                    foreach (string item in Directory.GetFileSystemEntries(pending.Dequeue())) {
                        Program.NoLink(item);
                        if (Directory.Exists(item)) pending.Enqueue(item);
                        else {
                            string relative = item.Substring(directory.Length + 1).Replace('\\','/');
                            if (!expected.Contains(relative) || ++seen > expected.Count) return false;
                        }
                    }
                }
                if (seen != expected.Count) return false;
                foreach (RuntimeFile file in runtime.files) {
                    token.ThrowIfCancellationRequested();
                    string path = Program.Child(directory,file.path);
                    if (new FileInfo(path).Length != file.size || Program.Sha256(path) != file.sha256) return false;
                }
                return true;
            } catch (IOException) { return false; }
            catch (UnauthorizedAccessException) { return false; }
        }

        internal async Task EnsureRuntime(string root, CancellationToken token, IEnumerable<string> previousInstallations = null) {
            string target = Program.Child(root,runtime.id);
            Status("Verificando bibliotecas já instaladas…",0);
            if (ValidRuntime(target,token)) { Status("Bibliotecas verificadas. Nenhum download necessário.",100); return; }
            // Migration from an older jpackage installation of the SAME variant. Read-only:
            // copy only manifest-approved runtime files, before MSI removes the previous app.
            foreach (string previous in previousInstallations ?? Program.InstalledRuntimes(app.upgradeUuid)) {
                if (!ValidRuntime(previous,token)) continue;
                string migration = Program.Child(root,runtime.id + ".staging-" + Guid.NewGuid().ToString("N"));
                Directory.CreateDirectory(migration);
                try {
                    Status("Reutilizando bibliotecas da instalação anterior…",95);
                    foreach (RuntimeFile file in runtime.files) {
                        token.ThrowIfCancellationRequested();
                        string destination = Program.Child(migration,file.path);
                        Directory.CreateDirectory(Path.GetDirectoryName(destination));
                        File.Copy(Program.Child(previous,file.path),destination,false);
                    }
                    if (!ValidRuntime(migration,token)) throw new IOException("Não foi possível verificar as bibliotecas anteriores.");
                    Promote(migration,target); return;
                } finally { if (Directory.Exists(migration)) DeleteStage(migration,root); }
            }
            string downloads = Program.Child(root,"downloads"); Directory.CreateDirectory(downloads); Program.NoLink(downloads);
            string archive = Program.Child(downloads,runtime.archiveSha256 + ".zip");
            if (!File.Exists(archive) || (File.GetAttributes(archive) & FileAttributes.ReparsePoint) != 0 ||
                new FileInfo(archive).Length != runtime.archiveSize || Program.Sha256(archive) != runtime.archiveSha256) {
                await Download(archive,token);
            }
            string stage = Program.Child(root,runtime.id + ".staging-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(stage);
            try {
                Status("Instalando e verificando bibliotecas de reprodução…",95);
                Extract(archive,stage,token);
                if (!ValidRuntime(stage,token)) throw new IOException("As bibliotecas não passaram na verificação de integridade.");
                token.ThrowIfCancellationRequested();
                // Never delete an existing runtime: preserve it even after a repair.
                Promote(stage,target);
            } finally {
                if (Directory.Exists(stage)) DeleteStage(stage,root);
            }
            Status("Bibliotecas prontas.",100);
        }

        private static void Promote(string stage,string target) {
            string backup = null;
            if (Directory.Exists(target)) {
                backup = target + ".previous-" + Guid.NewGuid().ToString("N");
                Directory.Move(target,backup);
            }
            try { Directory.Move(stage,target); }
            catch {
                if (backup != null && !Directory.Exists(target)) Directory.Move(backup,target);
                throw;
            }
        }

        internal async Task Download(string archive, CancellationToken token) {
            string partial = archive + ".partial-" + Guid.NewGuid().ToString("N");
            Status("Conectando ao servidor de bibliotecas…",0);
            try {
                using (CancellationTokenSource deadline = CancellationTokenSource.CreateLinkedTokenSource(token)) {
                    deadline.CancelAfter(TimeSpan.FromMinutes(15));
                    using (HttpClientHandler handler = new HttpClientHandler { AllowAutoRedirect = false, UseCookies = false, UseDefaultCredentials = false })
                    using (HttpClient client = new HttpClient(handler))
                    using (deadline.Token.Register(() => client.Dispose())) {
                        client.Timeout = TimeSpan.FromMinutes(15);
                        Uri address = new Uri(runtime.url);
                        for (int redirects = 0; ; redirects++) {
                            using (HttpResponseMessage response = await client.GetAsync(address,HttpCompletionOption.ResponseHeadersRead,deadline.Token)) {
                                int status = (int)response.StatusCode;
                                if (status >= 300 && status < 400) {
                                    if (redirects >= 3 || response.Headers.Location == null) throw new IOException("O servidor excedeu o limite de redirecionamentos.");
                                    Uri next = new Uri(address,response.Headers.Location);
                                    if (next.Scheme != "https" || next.UserInfo != "" ||
                                        !(next.Host == "videolan.org" || next.Host.EndsWith(".videolan.org",StringComparison.OrdinalIgnoreCase)))
                                        throw new IOException("O servidor informou um endereço de download não permitido.");
                                    address = next; continue;
                                }
                                response.EnsureSuccessStatusCode();
                                if (response.Content.Headers.ContentLength.HasValue && response.Content.Headers.ContentLength.Value != runtime.archiveSize)
                                    throw new IOException("O tamanho do download não corresponde ao esperado.");
                                using (Stream input = await response.Content.ReadAsStreamAsync())
                                using (Stream output = new FileStream(partial,FileMode.CreateNew,FileAccess.Write,FileShare.None)) {
                                    byte[] buffer = new byte[65536]; long received = 0; long lastPercent = -1;
                                    while (true) {
                                        int count = await input.ReadAsync(buffer,0,buffer.Length,deadline.Token);
                                        if (count == 0) break;
                                        received += count;
                                        if (received > runtime.archiveSize) throw new IOException("O download excedeu o tamanho esperado.");
                                        await output.WriteAsync(buffer,0,count,deadline.Token);
                                        long percent = received * 100 / runtime.archiveSize;
                                        if (percent != lastPercent) {
                                            Status("Baixando bibliotecas de reprodução… " + percent + "%",(int)(received * 90 / runtime.archiveSize));
                                            lastPercent = percent;
                                        }
                                    }
                                    if (received != runtime.archiveSize) throw new IOException("O download foi interrompido antes de terminar.");
                                }
                                break;
                            }
                        }
                    }
                }
                token.ThrowIfCancellationRequested();
                Status("Verificando o download…",92);
                if (Program.Sha256(partial) != runtime.archiveSha256) throw new IOException("O download não passou na verificação SHA-256.");
                if (File.Exists(archive)) File.Delete(archive);
                File.Move(partial,archive);
            } catch (Exception) {
                token.ThrowIfCancellationRequested();
                throw new IOException("Falha ao baixar as bibliotecas. Confira a conexão e tente novamente.");
            } finally { if (File.Exists(partial)) File.Delete(partial); }
        }

        internal void Extract(string archive, string stage, CancellationToken token) {
            Dictionary<string,RuntimeFile> files = runtime.files.ToDictionary(file => file.path,StringComparer.Ordinal);
            HashSet<string> extracted = new HashSet<string>(StringComparer.Ordinal);
            using (ZipArchive zip = ZipFile.OpenRead(archive)) {
                if (zip.Entries.Count > 10000) throw new IOException("Arquivo de bibliotecas inválido.");
                foreach (ZipArchiveEntry entry in zip.Entries) {
                    token.ThrowIfCancellationRequested();
                    if (!entry.FullName.StartsWith(runtime.archivePrefix,StringComparison.Ordinal)) continue;
                    string relative = entry.FullName.Substring(runtime.archivePrefix.Length);
                    RuntimeFile definition;
                    if (!files.TryGetValue(relative,out definition)) continue;
                    if (!extracted.Add(relative) || entry.Length != definition.size || (entry.ExternalAttributes >> 16 & 0xf000) == 0xa000)
                        throw new IOException("Entrada inválida nas bibliotecas.");
                    string path = Program.Child(stage,relative);
                    Directory.CreateDirectory(Path.GetDirectoryName(path));
                    using (Stream input = entry.Open()) using (Stream output = new FileStream(path,FileMode.CreateNew)) {
                        byte[] buffer = new byte[65536]; long count = 0;
                        while (true) {
                            token.ThrowIfCancellationRequested();
                            int received = input.Read(buffer,0,buffer.Length); if (received == 0) break;
                            count += received; if (count > definition.size) throw new IOException("Biblioteca excedeu o tamanho esperado.");
                            output.Write(buffer,0,received);
                        }
                        if (count != definition.size) throw new IOException("Biblioteca incompleta.");
                    }
                }
            }
            // Same provenance text emitted by PrepareVlcRuntime, pinned in the file manifest.
            string notice = "Unmodified VLC " + runtime.version + " Windows " + runtime.architecture + " runtime\nSource: " + runtime.url +
                "\nSHA-256: " + runtime.archiveSha256 + "\nVideoLAN licenses and authors are included.\n";
            File.WriteAllText(Program.Child(stage,"SQUI-TV-RUNTIME.txt"),notice,new UTF8Encoding(false));
        }

        internal string PrepareMsi(string root, CancellationToken token) {
            return PreparePayload(root,"squitv.application.msi",app.msiSha256,app.msiSize,".msi",token);
        }
        internal string PrepareUserTransform(string root, CancellationToken token) {
            return PreparePayload(root,"squitv.legacy-user.mst",app.mstSha256,app.mstSize,".mst",token);
        }
        private string PreparePayload(string root,string resource,string hash,long expectedSize,string extension,CancellationToken token) {
            string directory = Program.Child(root,"installers"); Directory.CreateDirectory(directory); Program.NoLink(directory);
            string path = Program.Child(directory,hash + extension);
            if (File.Exists(path)) {
                Program.NoLink(path);
                if (new FileInfo(path).Length == expectedSize && Program.Sha256(path) == hash) return path;
                File.Move(path,path + ".previous-" + Guid.NewGuid().ToString("N"));
            }
            string partial = path + ".partial-" + Guid.NewGuid().ToString("N");
            try {
                using (Stream source = Assembly.GetExecutingAssembly().GetManifestResourceStream(resource))
                using (Stream output = new FileStream(partial,FileMode.CreateNew)) {
                    if (source == null) throw new IOException("Pacote de instalação ausente.");
                    byte[] buffer = new byte[65536]; long size = 0;
                    while (true) {
                        token.ThrowIfCancellationRequested();
                        int count = source.Read(buffer,0,buffer.Length); if (count == 0) break;
                        size += count; if (size > expectedSize) throw new IOException("Pacote de instalação inválido.");
                        output.Write(buffer,0,count);
                    }
                    if (size != expectedSize) throw new IOException("Pacote de instalação incompleto.");
                }
                if (Program.Sha256(partial) != hash) throw new IOException("O pacote de instalação não passou na verificação.");
                File.Move(partial,path); return path;
            } finally { if (File.Exists(partial)) File.Delete(partial); }
        }

        private static void DeleteStage(string stage,string root) {
            string full = Path.GetFullPath(stage);
            if (!full.StartsWith(Path.GetFullPath(root).TrimEnd('\\') + "\\",StringComparison.OrdinalIgnoreCase) ||
                !Path.GetFileName(full).Contains(".staging-")) throw new IOException("Diretório temporário inválido.");
            Program.NoLink(full);
            foreach (string item in Directory.GetFileSystemEntries(full)) {
                Program.NoLink(item);
                if (Directory.Exists(item)) DeleteStageChild(item); else File.Delete(item);
            }
            Directory.Delete(full);
        }
        private static void DeleteStageChild(string directory) {
            Program.NoLink(directory);
            foreach (string item in Directory.GetFileSystemEntries(directory)) {
                Program.NoLink(item);
                if (Directory.Exists(item)) DeleteStageChild(item); else File.Delete(item);
            }
            Directory.Delete(directory);
        }
    }
}
