# Squi TV — correções do feedback e revisão adicional

> Relatório histórico da primeira etapa. O [relatório atualizado de backup e sincronização local](relatorio-sincronizacao-local-2026-10-29.md) substitui as informações sobre Firebase, exclusão de favoritos, limite do arquivo, perfis e pendências já corrigidas.

Data: 29/10/2026. Projeto: versão 1.17, versionCode 18. Base analisada: `a772579`.

## Correções implementadas

| Área | Problema encontrado | Alteração realizada |
|---|---|---|
| Favoritar na lista | Os cards apenas mostravam o coração; a ação exigia abrir outra tela. | Botão de adicionar/remover nos cards de canais, filmes e séries, nas categorias, biblioteca, busca e obras por ator. Mantidos metadados do conteúdo e ações separadas de abrir/reproduzir. |
| Voltar dos detalhes | A grade forçava o índice zero; efeitos de navegação recarregavam o catálogo e reinicializavam o foco. | Estado salvo de posição, deslocamento e seleção; reaproveitamento do conteúdo carregado; gênero, busca e ordenação preservados no ViewModel. A busca também restaura a seleção e a rolagem. |
| Favoritos em tempo real | A categoria especial lia apenas uma fotografia dos favoritos. | Observação das alterações locais e atualização da categoria aberta; remoção acompanha o card vizinho. Metadados do catálogo enriquecem os favoritos para permitir a filtragem por gênero. |
| Backup | Não existia exportação/importação de organização; o arquivo de configurações está excluído do backup automático do Android. | Uma entrada “Backup de configurações” reúne exportar e importar. Arquivo versionado, limite de 2 MB, validação e confirmação antes de aplicar; gravação das configurações em uma transação. |
| Conteúdo do backup | Organização e credenciais compartilham o mesmo DataStore. | Exportação por lista explícita: preferências, ordem/visibilidade das categorias, nomes personalizados e seções iniciais. Favoritos, histórico, downloads, senhas, PIN e sessão ficam fora do arquivo e são preservados na importação. |
| Mover para o topo | Só havia deslocamento de uma posição por operação. | Ação direta para a primeira posição, preservando a ordem relativa das outras categorias. Gravação usa o estado mais recente; rolagem acompanha o item. Controles adaptados para telas estreitas. |
| Troca de DNS | A personalização era localizada por servidor + usuário; mudar o endereço deixava a organização anterior inacessível. | Após autenticação bem-sucedida no novo DNS, copia a personalização da mesma conta quando o destino ainda não tem aquela configuração. Mantém os dados de origem. |
| Categorias especiais e bloqueadas | “Continuar assistindo” podia ser invalidado; categorias bloqueadas eram sempre removidas da lista. | Categoria especial reconhecida e opção de ocultar bloqueadas respeitada, permitindo o fluxo de desbloqueio por PIN quando a ocultação está desligada. |
| Proteção da organização | Restaurar o padrão era imediato; erros de leitura podiam ser substituídos por mapas vazios durante gravações. | Confirmação antes de restaurar a aba e leitura estrita dos mapas nas novas operações de gravação, evitando sobrescrever dados inválidos silenciosamente. |

### Como usar o backup

Configurações → Backup de configurações → Exportar configurações ou Importar configurações.

A importação informa quantas contas foram reconhecidas. A organização é aplicada somente às contas cadastradas com o mesmo usuário e servidor; as demais são informadas e ignoradas. As preferências gerais podem ser importadas independentemente disso. Categorias novas continuam disponíveis porque a ordenação personalizada aceita IDs ausentes ou recém-adicionados.

Recuperar uma organização já perdida depende de uma cópia anterior ou de dados antigos ainda existentes no dispositivo. A troca de DNS foi identificada como um mecanismo concreto de perda de acesso à personalização; a causa exata do relato original não foi reproduzida em aparelho.

## Revisão adicional: pendências encontradas

Os pontos abaixo foram identificados por leitura do código e exigem uma etapa própria de correção ou medição.

| Prioridade | Achado | Efeito possível | Próxima ação recomendada |
|---|---|---|---|
| Alta | O identificador do favorito no Firebase contém provedor e ID, mas não o tipo do conteúdo. | Canal, filme e série com o mesmo ID podem sobrescrever o mesmo documento remoto. | Identidade que inclua tipo e migração compatível dos documentos existentes. |
| Alta | A chave dos favoritos locais não contém conta ou perfil. | Favoritos podem se misturar entre contas/perfis que reutilizem IDs. | Migração do banco e das consultas para separar conta, perfil, tipo e conteúdo; alinhar a fila de sincronização. |
| Alta para instalações antigas | Há lacunas nos caminhos de migração registrados do banco, por exemplo entre 17 e 21. | Atualizar uma instalação com uma dessas bases antigas pode impedir a abertura do banco. | Identificar as versões efetivamente distribuídas, recuperar seus schemas e completar as migrações preservando os dados. |
| Média | O observador de progresso remoto usa um `collect` contínuo dentro do observador de perfil. | A assinatura anterior pode continuar ativa e impedir acompanhar o próximo perfil. | Trocar a assinatura quando o perfil mudar e cancelar o trabalho ligado ao perfil anterior. |
| Média | O bloqueio do app é reativado a cada emissão de configurações quando está habilitado e a sessão está desbloqueada. | Alterações comuns podem solicitar o PIN novamente. | Separar o ciclo de bloqueio da sessão das alterações ordinárias de preferências. |
| Média, a medir | A criação do SettingsRepository usa `runBlocking` para ler/migrar o DataStore. | Pode atrasar a inicialização em aparelhos lentos. | Medir a abertura e mover a inicialização para um fluxo assíncrono, preservando a migração das credenciais. |

## Compilação e limites da validação

| Verificação | Resultado |
|---|---|
| `:app:assembleDebug` | Concluída com sucesso após as alterações finais. |
| `:app:compileReleaseKotlin` | Concluída com sucesso após as alterações finais. |
| `git diff --check` | Sem erros de espaços ou conflitos no diff. |
| Identidade do APK debug | `rsv.squitv.debug`, versão `1.17-debug`, versionCode `18`, nome “Squi TV Debug”. |
| Testes automatizados | Não executados nesta etapa. |
| Uso em celular/TV | Pendente: retorno de detalhes, foco do controle remoto, toque nos corações, movimentação e exportação/importação com o seletor do aparelho. |

O build confirma a compilação; o comportamento visual e os fluxos de uso precisam de validação em aparelho. O APK disponibilizado abaixo é o debug gerado nesta etapa.

## Arquivos e evidências

- [APK debug](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/build/outputs/apk/debug/app-debug.apk>)
- [Log da compilação](</C:/Users/rafas/Documents/ChatGPT/Squi TV(Android)/build-feedback.log>)
- [Estado da grade](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/ui/content/components/ContentGridState.kt:11>)
- [Carregamento e favoritos](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/ui/viewmodel/CategoryViewModel.kt:117>)
- [Formato e validação do backup](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/data/model/SettingsBackup.kt:112>)
- [Importação e persistência](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/data/repository/SettingsRepository.kt:839>)
- [Migração da personalização na troca de DNS](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/data/repository/SettingsRepository.kt:918>)
- [Movimentação de categorias](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/ui/settings/ContentCustomizationScreen.kt:345>)
- [Identidade dos favoritos no Firebase](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/data/repository/FirebaseRepository.kt:94>)
- [Chave local dos favoritos](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/data/local/entities/FavoriteEntity.kt:5>)
- [Migrações registradas](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/di/DatabaseModule.kt:24>)
- [Observação do perfil e progresso remoto](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/ui/viewmodel/library/LibraryViewModel.kt:93>)
- [Bloqueio da sessão](</C:/Users/rafas/Documents/Android Studio Projetos/Squi TV/app/src/main/java/rsv/squitv/core/domain/interactor/SecurityManager.kt:35>)

O estado da interface utiliza os mecanismos de [estado salvo do Compose](https://developer.android.com/develop/ui/compose/state-saving). A seleção de arquivos segue o [Storage Access Framework do Android](https://developer.android.com/training/data-storage/shared/documents-files), com escolha do arquivo pelo usuário.
