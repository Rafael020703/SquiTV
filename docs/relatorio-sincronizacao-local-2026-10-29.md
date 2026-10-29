# Squi TV — backup seletivo e sincronização local

> A [atualização da sincronização sem código](sincronizacao-sem-codigo-2026-10-29.md) substitui o fluxo de pareamento e a descrição do protocolo de rede deste relatório.

Data: 29/10/2026. Base: `a772579`, versão 1.17, versionCode 18.

## Estado atual do desenvolvimento

As alterações estão aplicadas no projeto. A compilação debug e a compilação Kotlin release foram concluídas; a validação de uso entre um celular e uma TV está pendente.

| Área | O que foi implementado |
|---|---|
| Firebase | Removidos SDKs, plugins, arquivos de configuração, serviço de mensagens e chamadas remotas. Perfis, favoritos e progresso passam a depender da persistência local. As notificações remotas do Firebase deixam de existir. |
| Backup por arquivo | Formato v2, leitura compatível com v1, limite de 10 MB e validação antes de aplicar. Há 25 opções; todas começam marcadas. Na importação, todos os itens presentes no arquivo começam marcados. |
| Organização | Ordem personalizada, categorias ocultas, seções iniciais e nomes personalizados incluídos. Itens desmarcados mantêm os valores do destino. |
| Favoritos | Incluídos no backup e na transferência. A importação mescla os registros com os favoritos existentes. A identidade inclui conta, perfil, tipo e ID do conteúdo. |
| Migração local | Banco 25 → 26 preserva os registros existentes. Como os favoritos antigos não identificavam a conta/perfil de origem, eles são associados à primeira conta/perfil ativo disponível após a atualização. |
| Perfis | Cadastro e seleção locais, incluídos no backup sem PIN. A importação mescla por ID e preserva os PINs já existentes no destino. |
| Recepção na TV | Tela abre um servidor local em porta disponível, anuncia o aparelho na rede e exibe código temporário e endereço. Aplica apenas os itens permitidos pela seleção da TV e presentes no envio. |
| Envio pelo celular | Descoberta automática via Android NSD/mDNS, escolha do aparelho, código de pareamento e alternativa por IP:porta. O celular mostra sucesso depois da confirmação de aplicação pela TV. |
| Proteção da transferência | Código aleatório de 8 caracteres, desafio de autenticação e conteúdo cifrado com AES-GCM. Sessão de até 10 minutos, encerrada após receber um envio, fechar a tela ou deixar o app em segundo plano. |
| Mudança de idioma | A transferência sobrevive à recriação da Activity por configuração, incluindo a troca de idioma importada. |
| Bloqueio por PIN | Alterar preferências comuns deixa de reativar o bloqueio do aplicativo. Mudanças na conta ou na configuração do bloqueio continuam iniciando uma nova proteção de sessão. |
| Inicialização | Removida a leitura bloqueante de migração do construtor do SettingsRepository; inicialização passa ao fluxo assíncrono com exclusão mútua. |
| Correções anteriores | Mantidos o botão de favorito nos cards, a restauração da posição das listas e o botão de mover categoria diretamente para o topo. |

## Como usar

1. Instale esta versão do app no celular e na TV e mantenha ambos na mesma rede local.
2. Na TV: **Configurações → Backup e sincronização**. Revise os itens marcados e escolha **Receber pela rede local**.
3. No celular: abra a mesma área, escolha os itens e **Enviar pela rede local**. Selecione a TV encontrada e digite o código exibido nela.
4. Aguarde a confirmação nos dois aparelhos. A TV aplica os dados selecionados automaticamente. Se a descoberta falhar, use **Conectar pelo endereço** com o IP:porta mostrado na TV.

A transferência acontece sob demanda, a cada envio. A sessão de recepção termina quando um envio é concluído.

A organização e os favoritos são associados ao mesmo servidor e usuário. Essas contas precisam estar cadastradas no destino; seus dados de login não são transferidos. Contas ou perfis não reconhecidos geram aviso de aplicação parcial. Ao transferir favoritos de outros perfis, mantenha **Perfis** marcado ou cadastre os mesmos IDs no destino por uma importação anterior.

As preferências disponíveis incluem reprodução, buffer, tema, economia de dados, zoom, idioma, intervalo de atualização do catálogo, reprodução automática, proporção, diagnósticos, modo compacto, ocultação de categorias bloqueadas, downloads por Wi-Fi, downloads inteligentes, idiomas de áudio/legendas, notificações detalhadas, áudio em segundo plano e PiP. A elas se somam as seis opções de organização, perfis e favoritos.

Senhas de login, PINs, histórico de reprodução/pesquisa e arquivos baixados não fazem parte deste backup. Favoritos e perfis que existiam apenas no antigo serviço remoto não são recuperados automaticamente; os dados locais são preservados.

## Compilação e revisão

| Verificação | Resultado |
|---|---|
| `:app:assembleDebug` | Concluída com sucesso. |
| `:app:compileReleaseKotlin` | Concluída com sucesso. |
| Identidade do APK | `rsv.squitv.debug`, `1.17-debug`, versionCode `18`, nome **Squi TV Debug**. Permite instalar ao lado da release. |
| Revisão do diff | `git diff --check` sem erros. |
| Fonte de produção | Sem referências a Firebase, Firestore, FCM ou plugin Google Services nos arquivos inspecionados. |
| Migração 25 → 26 | Estrutura revisada contra o schema Room 26 exportado; não executada em um banco de aparelho nesta etapa. |
| Testes automatizados | Não executados nesta etapa. |
| Celular e TV reais | Pendente validar descoberta, código inválido, envio, seleção parcial, retorno das telas e foco do controle remoto. |

A importação usa uma transação Room e uma edição transacional do DataStore. Esses dois mecanismos não formam uma transação distribuída: uma falha rara no commit do banco após a gravação das preferências pode deixar uma aplicação parcial. As mensagens de erro não prometem desfazer tudo.

APK debug: 32.1 MiB. SHA-256: `73B089EE4B298B5900C24DC3A2D907DE409BE01344B6B0E691FBAB2BBA3594D6`.

## Próximas pendências

| Prioridade | Pendência | Motivo |
|---|---|---|
| Alta | Validar a transferência e a migração em celular/TV com dados reais. | Compilação não comprova descoberta na rede, comportamento do roteador ou aplicação em um banco instalado. |
| Alta para instalações antigas | Recuperar schemas históricos e completar as lacunas de migração anteriores à versão 21. | O projeto não contém evidências suficientes para reconstruir essas bases sem risco de perda de dados. |
| Média | Separar também histórico/progresso por conta e perfil. | A separação implementada nesta etapa cobre favoritos; o histórico local ainda usa a identidade anterior. |

## Arquivos e evidências

- [APK debug](../app/build/outputs/apk/debug/app-debug.apk)
- [Log de compilação](<C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/build-local-backup.log>)
- [Formato e seleção do backup](../app/src/main/java/rsv/squitv/data/model/SettingsBackup.kt)
- [Aplicação de configurações e favoritos](../app/src/main/java/rsv/squitv/data/repository/SettingsBackupRepository.kt)
- [Descoberta, servidor e envio](../app/src/main/java/rsv/squitv/ui/viewmodel/settings/LocalTransferViewModel.kt)
- [Protocolo de transferência](../app/src/main/java/rsv/squitv/data/transfer/LocalTransferProtocol.kt)
- [Tela de transferência](../app/src/main/java/rsv/squitv/ui/settings/LocalTransferDialog.kt)
- [Schema Room 26](../app/schemas/rsv.squitv.data.local.AppDatabase/26.json)

Referências da implementação: [descoberta de serviços NSD](https://developer.android.com/develop/connectivity/wifi/use-nsd), [acesso à rede local no Android](https://developer.android.com/privacy-and-security/local-network-permission) e [migrações Room](https://developer.android.com/training/data-storage/room/migrating-db-versions).
