# Squi TV — sincronização sem digitar código

Data: 29/10/2026. Aplicativo 1.17, versionCode 18.

## O que mudou

| Área | Implementação |
|---|---|
| Tela inicial | Ações Receber neste aparelho e Enviar para outro aparelho. As 25 opções ficam recolhidas em Escolher itens, com tudo marcado por padrão. Exportação e importação por arquivo continuam disponíveis. |
| Celular | Busca os aparelhos na rede. Tocar no aparelho inicia o envio, sem seleção por rádio, botão adicional de envio ou código. |
| TV | Mostra o aparelho solicitante, seu endereço e os itens efetivamente permitidos. Aceitar e receber aplica a configuração; Recusar descarta a solicitação. |
| Resultado | O celular aguarda a resposta da TV e só confirma sucesso depois da aplicação. Recusa, expiração e aplicação parcial têm mensagens próprias. |
| Detalhes | Endereço manual continua disponível como alternativa; no receptor o endereço fica recolhido em Detalhes da conexão. |
| Idiomas | Textos em português, inglês e espanhol. |

## Como usar

1. Use esta versão do Squi TV nos dois aparelhos, conectados à mesma rede local.
2. Na TV: Configurações → Backup e sincronização → Receber neste aparelho.
3. No celular: Enviar para outro aparelho → toque no nome da TV.
4. Na TV: Aceitar e receber. Aguarde a confirmação de aplicação.

A solicitação aguarda resposta por até 1 minuto. A recepção fica disponível por até 10 minutos e termina após um envio concluído. Fechar a tela ou deixar o app em segundo plano encerra a sessão; uma recriação por mudança de configuração preserva a transferência.

A seleção aplicada é a interseção dos itens presentes no envio com os escolhidos no receptor. Favoritos continuam mesclados. Organização/favoritos exigem a mesma conta cadastrada no destino. Credenciais e PINs continuam fora da transferência.

## Protocolo

A transferência usa agora o protocolo de rede v2: troca de chaves ECDH P-256, derivação HKDF-SHA256 e AES-GCM com chaves distintas por direção. A descoberta anuncia a versão e a impressão digital da chave temporária do receptor; o remetente confere essa impressão antes de enviar. Isso não altera o formato dos arquivos de backup v2/v1.

O fluxo foi projetado para uma rede local confiável. Nome e anúncio NSD não comprovam a identidade de uma pessoa/dispositivo contra um atacante ativo na rede. A confirmação na TV controla a aplicação dos dados. A conexão manual não possui a impressão digital obtida pela descoberta. Esta etapa não representa uma auditoria criptográfica independente.

As versões anteriores do app usam o protocolo de rede com código e precisam ser atualizadas nos dois aparelhos. A interface informa quando encontra um receptor com protocolo anterior.

## Compilação

Comando executado:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:compileReleaseKotlin --console=plain
```

| Verificação | Resultado |
|---|---|
| APK debug | Compilado com sucesso; rsv.squitv.debug, Squi TV Debug, versão 1.17-debug. |
| APK de testes instrumentados | Compilado com sucesso. |
| Kotlin release | Compilado com sucesso. |
| Configuration cache | Entrada armazenada. |
| Revisão do diff | Sem erros de whitespace; Git informa conversão de LF/CRLF em arquivos do checkout. |
| Execução de testes | Não executada nesta etapa. Os 191 testes citados em relatórios anteriores não foram reexecutados aqui. |
| Uso real e revisão visual | Pendente: nenhum aparelho conectado via ADB. |

A compilação também informa depreciações para uma futura migração ao Gradle 10; o projeto permanece no Gradle 9.8.2 e AGP 9.4.0 definidos na etapa anterior.

## Validação pendente em aparelhos

- Descoberta automática e alternativa por endereço.
- Envio aceito, recusado e sem resposta; tentativa após expiração.
- Itens desmarcados no receptor e contas/perfis não reconhecidos.
- Fechar a tela, ir ao segundo plano e importar outro idioma.
- Foco e rolagem com controle remoto; seleção dos itens por toque.

## Artefatos

- [APK debug](../app/build/outputs/apk/debug/app-debug.apk), 32.5 MiB.
- SHA-256: `C7ACFD7F2B9B637D062BE6410BCF13EC9269CCC5F0109AE2345FC31B5B67D2BD`.
- [Log da compilação](<C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/build-sync-sem-codigo.log>)
- [Tela de transferência](../app/src/main/java/rsv/squitv/ui/settings/LocalTransferDialog.kt)
- [Estado e conexão](../app/src/main/java/rsv/squitv/ui/viewmodel/settings/LocalTransferViewModel.kt)
- [Protocolo](../app/src/main/java/rsv/squitv/data/transfer/LocalTransferProtocol.kt)

Referências: [descoberta NSD](https://developer.android.com/develop/connectivity/wifi/use-nsd) e [KeyAgreement do Android](https://developer.android.com/reference/javax/crypto/KeyAgreement).
