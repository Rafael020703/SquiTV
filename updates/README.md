# Referências de atualização por plataforma

Os aplicativos resolvem releases publicadas no mesmo repositório. Quando o histórico limitado não é suficiente, consultam `stable/android.json` ou `stable/windows.json` pela GitHub Contents API na branch padrão.

Não criar referências para versões ainda não publicadas. `tools/prepare_release.py` gera candidatos locais em `channel-index/`; primeiro publicar/verificar a release e seus assets, depois copiar somente as referências das plataformas atualizadas para `updates/stable/` em uma alteração autorizada.

Cada referência contém plataforma, versão, tag e SHA-256 do manifesto final. Não contém URLs arbitrárias, credenciais nem instaladores. Não reapontar tags antigas ou sobrescrever seus assets. Referências de exemplo ou placeholders não devem ser adicionadas aqui.

Consultar [o contrato](../docs/contrato-atualizacoes.md) e [o checklist](../docs/checklist-release.md).

Referências atuais: release conjunta [v1.23](https://github.com/Rafael020703/SquiTV/releases/tag/v1.23), Android 1.23/code 24 e Windows 1.23.0. Ambas foram avançadas após a conferência dos cinco assets publicados e do SHA-256 do manifesto; cada aplicativo seleciona seu artefato e suas notas.
