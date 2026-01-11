package banca;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CurrencyExchangeAgent extends Agent {
    
    private Map<String, Double> exchangeRates = new HashMap<>();
    private Random random = new Random();
    
    protected void setup() {
        System.out.println(getLocalName() + " started.");
        
        // Initialize with some base rates
        exchangeRates.put("RON_EUR", 0.20);  // 1 RON = 0.20 EUR
        exchangeRates.put("RON_USD", 0.22);  // 1 RON = 0.22 USD
        exchangeRates.put("EUR_USD", 1.10);  // 1 EUR = 1.10 USD
        exchangeRates.put("EUR_RON", 5.00);  // 1 EUR = 5.00 RON
        exchangeRates.put("USD_RON", 4.55);  // 1 USD = 4.55 RON
        exchangeRates.put("USD_EUR", 0.91);  // 1 USD = 0.91 EUR
        
        registerWithDF();
        
        addBehaviour(new TickerBehaviour(this, 5000) {
            protected void onTick() {
                updateExchangeRates();
            }
        });
        
        addBehaviour(new ExchangeRateRequestBehaviour());
    }
    
    private void registerWithDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("currency-exchange");
        sd.setName("bt-currency-exchange-service");
        dfd.addServices(sd);
        
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " registered as currency exchange service.");
        } catch (FIPAException e) {
            System.err.println(getLocalName() + " failed to register with DF: " + e.getMessage());
        }
    }
    
    private void updateExchangeRates() {
        // Randomly adjust rates by ±2%
        for (Map.Entry<String, Double> entry : exchangeRates.entrySet()) {
            double currentRate = entry.getValue();
            double change = 1 + ((random.nextDouble() * 0.04) - 0.02);
            double newRate = currentRate * change;
            
            // Keep reasonable bounds
            if (newRate > 0.01 && newRate < 100) {
                exchangeRates.put(entry.getKey(), Math.round(newRate * 10000.0) / 10000.0);
            }
        }
        
        // Ensure consistency
        if (exchangeRates.containsKey("RON_EUR") && exchangeRates.containsKey("EUR_RON")) {
            exchangeRates.put("EUR_RON", Math.round((1 / exchangeRates.get("RON_EUR")) * 100.0) / 100.0);
        }
        if (exchangeRates.containsKey("RON_USD") && exchangeRates.containsKey("USD_RON")) {
            exchangeRates.put("USD_RON", Math.round((1 / exchangeRates.get("RON_USD")) * 100.0) / 100.0);
        }
        if (exchangeRates.containsKey("EUR_USD") && exchangeRates.containsKey("USD_EUR")) {
            exchangeRates.put("USD_EUR", Math.round((1 / exchangeRates.get("EUR_USD")) * 100.0) / 100.0);
        }
        
        System.out.println(getLocalName() + " updated exchange rates");
    }
    
    private String formatRatesForGUI() {
        StringBuilder sb = new StringBuilder();
        sb.append("Exchange Rates:\n");
        sb.append(String.format("1 RON = %.4f EUR | 1 RON = %.4f USD\n", 
            exchangeRates.get("RON_EUR"), exchangeRates.get("RON_USD")));
        sb.append(String.format("1 EUR = %.4f USD | 1 EUR = %.2f RON\n",
            exchangeRates.get("EUR_USD"), exchangeRates.get("EUR_RON")));
        sb.append(String.format("1 USD = %.2f RON | 1 USD = %.4f EUR",
            exchangeRates.get("USD_RON"), exchangeRates.get("USD_EUR")));
        return sb.toString();
    }
    
    private class ExchangeRateRequestBehaviour extends jade.core.behaviours.CyclicBehaviour {
        public void action() {
            ACLMessage msg = receive();
            if (msg != null) {
                if ("GET_EXCHANGE_RATES".equals(msg.getConversationId())) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent(formatRatesForGUI());
                    send(reply);
                }
            } else {
                block();
            }
        }
    }
    
    public Map<String, Double> getExchangeRates() {
        return new HashMap<>(exchangeRates);
    }
}