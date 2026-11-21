using System;
using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;

internal static class WindowsDebugLauncher
{
    [STAThread]
    private static void Main()
    {
        try
        {
            string root = AppDomain.CurrentDomain.BaseDirectory;
            using (OpenDeployLock(Path.Combine(root, ".deploy.lock")))
            {
                string build = File.ReadAllText(Path.Combine(root, "active-build.txt")).Trim();
                if (!Regex.IsMatch(build, @"\A[a-f0-9]{16}\z"))
                    throw new InvalidDataException();
                string directory = Path.Combine(root, "builds", build);
                string executable = Path.Combine(directory, "SquiTVDebug.exe");
                if (!File.Exists(Path.Combine(directory, ".copy-complete")))
                    throw new InvalidDataException();
                using (Process.Start(new ProcessStartInfo(executable) {
                    UseShellExecute = false,
                    WorkingDirectory = directory
                })) { }
            }
        }
        catch (Exception)
        {
            MessageBox.Show("Não foi possível abrir a build atual. Se a debug estiver sendo atualizada, tente novamente em instantes.",
                "Squi TV Debug", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private static FileStream OpenDeployLock(string path)
    {
        for (int attempt = 0; ; attempt++)
        {
            try { return File.Open(path, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None); }
            catch (IOException)
            {
                if (attempt >= 40) throw;
                Thread.Sleep(250);
            }
        }
    }
}
