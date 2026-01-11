package banca;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.*;

public class BankGUI extends JFrame implements BankGUIUpdater {

    private GUIAgent agent;
    private JComboBox<String> branchBox;
    private JTextField accountField;
    private JTextField amountField;
    private JTextArea output;
    private JLabel exchangeRateLabel;
    private JTextArea notificationArea;
    private JButton viewNotificationsBtn;
    private JButton clearNotificationsBtn;
    private JButton setThresholdBtn;
    private JTextField thresholdField;
    private JTextField notifyAccountField;

    public BankGUI(GUIAgent agent) {
        this.agent = agent;
        initUI();
    }

    private void initUI() {
        setTitle("Banca Transilvania");
        setSize(600, 650);
        setLayout(new BorderLayout(5, 5));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Banking", createBankingPanel());
        tabbedPane.addTab("Notifications", createNotificationsPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        setVisible(true);
    }

    private JPanel createBankingPanel() {
        JPanel bankingPanel = new JPanel(new BorderLayout(10, 10));
        bankingPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel mainBankingPanel = new JPanel(new BorderLayout(5, 5));
        
        JPanel topPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("Account Operations"));
        
        topPanel.add(new JLabel("Branch:"));
        branchBox = new JComboBox<>();
        branchBox.addItem("Loading branches...");
        topPanel.add(branchBox);

        topPanel.add(new JLabel("Account ID:"));
        accountField = new JTextField();
        topPanel.add(accountField);

        topPanel.add(new JLabel("Amount:"));
        amountField = new JTextField();
        topPanel.add(amountField);
        
        mainBankingPanel.add(topPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        JButton openBtn = new JButton("Open Account");
        JButton depositBtn = new JButton("Deposit");
        JButton withdrawBtn = new JButton("Withdraw");
        JButton refreshRatesBtn = new JButton("Refresh Rates");
        JButton refreshBranchesBtn = new JButton("Refresh Branches");
        
        Dimension buttonSize = new Dimension(120, 30);
        openBtn.setPreferredSize(buttonSize);
        depositBtn.setPreferredSize(buttonSize);
        withdrawBtn.setPreferredSize(buttonSize);
        refreshRatesBtn.setPreferredSize(buttonSize);
        refreshBranchesBtn.setPreferredSize(new Dimension(140, 30));

        buttonPanel.add(openBtn);
        buttonPanel.add(depositBtn);
        buttonPanel.add(withdrawBtn);
        buttonPanel.add(refreshRatesBtn);
        buttonPanel.add(refreshBranchesBtn);
        
        mainBankingPanel.add(buttonPanel, BorderLayout.CENTER);

        JPanel exchangePanel = new JPanel(new BorderLayout());
        exchangePanel.setBorder(BorderFactory.createTitledBorder("Live Exchange Rates"));
        
        exchangeRateLabel = new JLabel("Exchange Rates: Starting up...");
        exchangeRateLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        exchangePanel.add(exchangeRateLabel, BorderLayout.CENTER);
        
        mainBankingPanel.add(exchangePanel, BorderLayout.SOUTH);

        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBorder(BorderFactory.createTitledBorder("Transaction Log"));
        
        output = new JTextArea();
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        
        JScrollPane outputScrollPane = new JScrollPane(output);
        outputScrollPane.setPreferredSize(new Dimension(500, 150));
        
        outputPanel.add(outputScrollPane, BorderLayout.CENTER);
        
        bankingPanel.add(mainBankingPanel, BorderLayout.NORTH);
        bankingPanel.add(outputPanel, BorderLayout.CENTER);

        openBtn.addActionListener(e -> openAccount());
        depositBtn.addActionListener(e -> deposit());
        withdrawBtn.addActionListener(e -> withdraw());
        refreshRatesBtn.addActionListener(e -> agent.requestExchangeRates());
        refreshBranchesBtn.addActionListener(e -> agent.refreshBranches());

        return bankingPanel;
    }

    private JPanel createNotificationsPanel() {
        JPanel notificationsPanel = new JPanel(new BorderLayout(10, 10));
        notificationsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel controlPanel = new JPanel(new GridLayout(2, 3, 5, 5));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Notification Controls"));
        
        controlPanel.add(new JLabel("Account ID:"));
        notifyAccountField = new JTextField();
        controlPanel.add(notifyAccountField);
        
        viewNotificationsBtn = new JButton("View Notifications");
        controlPanel.add(viewNotificationsBtn);
        
        controlPanel.add(new JLabel("Low Balance Threshold:"));
        thresholdField = new JTextField("100.0");
        controlPanel.add(thresholdField);
        
        setThresholdBtn = new JButton("Set Threshold");
        controlPanel.add(setThresholdBtn);
        
        clearNotificationsBtn = new JButton("Clear Notifications");
        JPanel clearPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        clearPanel.add(clearNotificationsBtn);
        
        notificationsPanel.add(controlPanel, BorderLayout.NORTH);
        notificationsPanel.add(clearPanel, BorderLayout.SOUTH);
        
        JPanel displayPanel = new JPanel(new BorderLayout());
        displayPanel.setBorder(BorderFactory.createTitledBorder("Notifications"));
        
        notificationArea = new JTextArea();
        notificationArea.setEditable(false);
        notificationArea.setLineWrap(true);
        notificationArea.setWrapStyleWord(true);
        
        JScrollPane scrollPane = new JScrollPane(notificationArea);
        scrollPane.setPreferredSize(new Dimension(500, 300));
        
        displayPanel.add(scrollPane, BorderLayout.CENTER);
        notificationsPanel.add(displayPanel, BorderLayout.CENTER);
        
        viewNotificationsBtn.addActionListener(e -> {
            String accountId = notifyAccountField.getText().trim();
            if (!accountId.isEmpty()) {
                notificationArea.setText("Loading notifications for " + accountId + "...");
                agent.requestNotifications(accountId);
            } else {
                notificationArea.setText("Please enter an account ID");
            }
        });
        
        setThresholdBtn.addActionListener(e -> {
            String accountId = notifyAccountField.getText().trim();
            String threshold = thresholdField.getText().trim();
            if (!accountId.isEmpty() && !threshold.isEmpty()) {
                try {
                    Double.parseDouble(threshold);
                    agent.setLowBalanceThreshold(accountId, threshold);
                    notificationArea.append("\nSet threshold for " + accountId + " to " + threshold);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Please enter a valid number for threshold");
                }
            } else {
                notificationArea.setText("Please enter both account ID and threshold");
            }
        });
        
        clearNotificationsBtn.addActionListener(e -> {
            String accountId = notifyAccountField.getText().trim();
            if (!accountId.isEmpty()) {
                agent.clearNotifications(accountId);
                notificationArea.setText("Cleared notifications for " + accountId);
            } else {
                notificationArea.setText("Please enter an account ID");
            }
        });
        
        return notificationsPanel;
    }

    @Override
    public void showNotification(String notification) {
        SwingUtilities.invokeLater(() -> {
            notificationArea.append(notification + "\n");
            notificationArea.setCaretPosition(notificationArea.getDocument().getLength());
        });
    }

    @Override
    public void updateNotificationArea(String text) {
        SwingUtilities.invokeLater(() -> {
            notificationArea.setText(text);
        });
    }

    private void openAccount() {
        String branch = (String) branchBox.getSelectedItem();
        String account = accountField.getText().trim();

        if (branch == null || branch.equals("Loading branches...") || account.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a valid branch and enter account ID");
            return;
        }

        agent.sendRequest(branch, "OPEN_ACCOUNT", account);
    }

    private void deposit() {
        String branch = (String) branchBox.getSelectedItem();
        String account = accountField.getText().trim();
        String amount = amountField.getText().trim();

        if (branch == null || branch.equals("Loading branches...") || account.isEmpty() || amount.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields and select a valid branch");
            return;
        }

        try {
            Double.parseDouble(amount);
            agent.sendRequest(branch, "DEPOSIT", account + ";" + amount);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter a valid number for amount");
        }
    }

    private void withdraw() {
        String branch = (String) branchBox.getSelectedItem();
        String account = accountField.getText().trim();
        String amount = amountField.getText().trim();

        if (branch == null || branch.equals("Loading branches...") || account.isEmpty() || amount.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields and select a valid branch");
            return;
        }

        try {
            Double.parseDouble(amount);
            agent.sendRequest(branch, "WITHDRAW", account + ";" + amount);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter a valid number for amount");
        }
    }

    @Override
    public void updateBranchesList(List<String> branches) {
        SwingUtilities.invokeLater(() -> {
            branchBox.removeAllItems();
            if (branches.isEmpty()) {
                branchBox.addItem("No branches found");
            } else {
                for (String branch : branches) {
                    branchBox.addItem(branch);
                }
                branchBox.setSelectedIndex(0);
            }
        });
    }

    @Override
    public void updateExchangeRates(String ratesText) {
        SwingUtilities.invokeLater(() -> {
            exchangeRateLabel.setText("<html>" + ratesText.replace("\n", "<br>") + "</html>");
        });
    }

    @Override
    public void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            if (output != null) {
                output.append(text + "\n");
                output.setCaretPosition(output.getDocument().getLength());
            }
        });
    }
}