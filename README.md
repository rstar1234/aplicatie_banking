# Aplicație Banking

Aplicație ce ajută la îndeplinirea mai multor tranzacții In cadrul Băncii Transilvania, cum ar fi deschiderea unui cont la orice filială a sa și la depunerea sau retragerea oricărei sume de bani. De asemenea, prezintă un sistem de notificare și afișează ratele curente de schimb valutar.

Dezvoltata cu librAriile Jade Si Swing in Java.

## Instalare

Clonați repository-ul:

```bash
git clone https://github.com/rstar1234/aplicatie_banking.git
```

## Configurare și Lansare in executie:

[Descărcați Jade](https://jade.tilab.com/download/jade/license/jade-download/) și adăugați jar-ul în proiect. Adăugați-l în build path și configurați aceasta astfel încât jar-ul să aibă prioritate în execuție. Creați o nouă configurare de lansare, selectați jade.Boot drept clasă principală și adăugați următoarele argumente pentru linia de comandă (vor crea și porni mai mulți agenți Jade):

```bash
-gui gui:banca.GUIAgent;exchange:banca.CurrencyExchangeAgent;GeorgeEnescu:banca.BankBranchAgent;Carrefour:banca.BankBranchAgent;notifier:banca.NotificationAgent
```

Lansați în execuție.
