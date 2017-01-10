/* Copyright (C) 2013 Interactive Brokers LLC. All rights reserved.  This code is subject to the terms
 * and conditions of the IB API Non-Commercial License or the IB API Commercial License, as applicable. */

package com.peterflanner.twspositionsizer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.InputMismatchException;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.peterflanner.twspositionsizer.ui.components.NewTabbedPanel.INewTab;

import com.ib.controller.AccountSummaryTag;
import com.ib.controller.NewContract;
import com.ib.controller.NewContractDetails;
import com.ib.controller.NewTickType;
import com.ib.controller.Types;
import com.ib.controller.ApiController.IAccountSummaryHandler;
import com.ib.controller.ApiController.IDisplayGroupHandler;
import com.ib.controller.ApiController.ITopMktDataHandler;
import com.ib.controller.ApiController.IContractDetailsHandler;
import com.peterflanner.twspositionsizer.ui.components.VerticalPanel;
import com.peterflanner.twspositionsizer.util.UIUtils;

public class PositionSizerPanel extends JPanel implements INewTab, IAccountSummaryHandler, IDisplayGroupHandler, ITopMktDataHandler, IContractDetailsHandler {
	private DefaultListModel<String> m_acctList = new DefaultListModel<>();
	private JList<String> m_accounts = new JList<>( m_acctList);
	private String m_selAcct = "";
	private JLabel m_lastUpdated = new JLabel();
	
	private JTextField netLiquidationTextField = new JTextField(7);
	private JTextField currentContractTextField = new JTextField(7);
	private JTextField currentPriceTextField = new JTextField(7);
	private JTextField riskTextField = new JTextField("0.5",7);
	private JTextField stopLossTextField = new JTextField(7);
	private JTextField sharesToBuyTextField = new JTextField(7);
	private JTextField valueOfSharesTextField = new JTextField(7);
	private Color originalDisabledBackgroundColor;
	
	private NumberFormat doubleZeroFormat = new DecimalFormat("0.00");
	private NumberFormat numberFormat = NumberFormat.getInstance();
	
	private volatile boolean wasAcctSummaryRequested = false;
	private double excessLiquidity = -1.0;
	private double totalCashValue = -1.0;
	private double buyingPower = -1.0;

	PositionSizerPanel() {
		m_accounts.setPreferredSize( new Dimension( 100, 100) );
        UIUtils.disableTextField(currentContractTextField);
		UIUtils.disableTextField(sharesToBuyTextField);
		UIUtils.disableTextField(valueOfSharesTextField);
		originalDisabledBackgroundColor = currentContractTextField.getBackground();

		// TODO add radio buttons to switch between relative and absolute
		JLabel stopLossLabel = new JLabel("Stop Loss (absolute)");
		MouseWheelListener stopLossMouseWheelListener = new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				try {
					double price = numberFormat.parse(stopLossTextField.getText()).doubleValue();
					int increment = -1 * e.getWheelRotation(); // negative up, positive down
					price = price + increment * 0.01;
					stopLossTextField.setText(doubleZeroFormat.format(price));
				} catch (ParseException pe) {
					// nothing to do
				}
			}
		};
		stopLossLabel.addMouseWheelListener(stopLossMouseWheelListener);
		stopLossTextField.addMouseWheelListener(stopLossMouseWheelListener);


		// TODO add radio buttons to switch between percent and absolute
		JLabel riskLabel = new JLabel("Risk (%)");
		MouseWheelListener riskMouseWheelListener = new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				try {
					double price = numberFormat.parse(riskTextField.getText()).doubleValue();
					int increment = -1 * e.getWheelRotation(); // negative up, positive down
					price = price + increment * 0.1;
					NumberFormat singleZeroFormat = new DecimalFormat("0.0");
					riskTextField.setText(singleZeroFormat.format(price));
				} catch (ParseException pe) {
					// nothing to do
				}
			}
		};
		riskLabel.addMouseWheelListener(riskMouseWheelListener);
		riskTextField.addMouseWheelListener(riskMouseWheelListener);

		JButton refreshButton = new JButton("Refresh");
		refreshButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				requestData();
			}
		});

		JButton calculateButton = new JButton("Calculate");
		calculateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				calculate();
			}
		});
		
		VerticalPanel mainPanel = new VerticalPanel();

		mainPanel.add("Net Liquidation", netLiquidationTextField);
		mainPanel.add("Current Contract", currentContractTextField);
		mainPanel.add("Current Price", currentPriceTextField);
		mainPanel.add(new Component[] {riskLabel, riskTextField});
		mainPanel.add(new Component[] {stopLossLabel, stopLossTextField});
		mainPanel.add("Shares to Buy", sharesToBuyTextField);
		mainPanel.add("Value of Shares", valueOfSharesTextField);
		mainPanel.add(new Component[] {refreshButton, calculateButton});

		setLayout(new BorderLayout());
		add(m_lastUpdated, BorderLayout.SOUTH);
		add(mainPanel);
		
		// TODO maybe we can just always select the first account or maybe we should add back in account selection
		m_accounts.addListSelectionListener( new ListSelectionListener() {
			@Override public void valueChanged(ListSelectionEvent e) {
				requestData();
			}
		});
	}

	private void calculate() {
		try {
            double nlv = doubleZeroFormat.parse(netLiquidationTextField.getText()).doubleValue();
            double currentPrice = numberFormat.parse(currentPriceTextField.getText()).doubleValue();
            try {
                double maxRiskPercent = numberFormat.parse(riskTextField.getText()).doubleValue(); // TODO change this when it becomes an option
                double stopLoss = numberFormat.parse(stopLossTextField.getText()).doubleValue(); // TODO change this when it becomes an option
                validateValues(nlv, currentPrice, maxRiskPercent, stopLoss);
                
                double maxRiskValue = nlv * maxRiskPercent / 100;

                int sharesToBuy = (int) (maxRiskValue / (currentPrice - stopLoss)); // truncation is fine, this is just an estimate
                sharesToBuyTextField.setText(String.valueOf(sharesToBuy));
                double valueOfShares = sharesToBuy * currentPrice;
                valueOfSharesTextField.setText(doubleZeroFormat.format(valueOfShares));
                
                // color code shares to buy and value of shares based on what account values it exceeds
                if (buyingPower >=0 && valueOfShares > buyingPower) {
                    sharesToBuyTextField.setBackground(Color.RED);
                    valueOfSharesTextField.setBackground(Color.RED);
                } else if (excessLiquidity >= 0 && valueOfShares > excessLiquidity) {
                    sharesToBuyTextField.setBackground(Color.ORANGE);
                    valueOfSharesTextField.setBackground(Color.ORANGE);
                } else if (totalCashValue >= 0 && valueOfShares > totalCashValue) {
                    sharesToBuyTextField.setBackground(Color.YELLOW);
                    valueOfSharesTextField.setBackground(Color.YELLOW);
                } else if (buyingPower >= 0 && excessLiquidity >= 0 && totalCashValue >= 0) {
                    sharesToBuyTextField.setBackground(Color.GREEN);
                    valueOfSharesTextField.setBackground(Color.GREEN);
                } else {
                    sharesToBuyTextField.setBackground(originalDisabledBackgroundColor);
                    valueOfSharesTextField.setBackground(originalDisabledBackgroundColor);
                }
            } catch (ParseException pe) {
                MainPanel.INSTANCE.show("Invalid value entered for Risk or Stop Loss.");
            } catch (InputMismatchException ie) {
                MainPanel.INSTANCE.show("Invalid input");
            }
        } catch (ParseException pe) {
            MainPanel.INSTANCE.show("Cannot calculate without Net Liquidating Value and Current Price.  Press Refresh to request these values again.");
        }
	}

	/** Called when the tab is first visited. */
	@Override public void activated() {
		for (String account : MainPanel.INSTANCE.accountList() ) {
			m_acctList.addElement( account);
		}
		
		if (MainPanel.INSTANCE.accountList().size() == 1) {
			m_accounts.setSelectedIndex( 0);
		}
	}
	
	public void disconnected() {
	    wasAcctSummaryRequested = false;
    }
	
	/** Called when the tab is closed by clicking the X. */
	@Override public void closed() {
	}

	protected synchronized void requestData() {
		int i = m_accounts.getSelectedIndex();
		if (i != -1) {
			String selAcct = m_acctList.get(i);
			if (selAcct != null && !selAcct.isEmpty()) {
				m_selAcct = selAcct;
				AccountSummaryTag[] tags = {AccountSummaryTag.NetLiquidation, AccountSummaryTag.ExcessLiquidity, AccountSummaryTag.TotalCashValue, AccountSummaryTag.BuyingPower};
				if (!wasAcctSummaryRequested) {
					MainPanel.INSTANCE.controller().reqAccountSummary("All", tags, this);
					wasAcctSummaryRequested = true;
				}
				MainPanel.INSTANCE.controller().queryDisplayGroups(this);
			}
		}
	}

	@Override
	public void accountSummary(String account, AccountSummaryTag accountSummaryTag, String value, String currency) {
	    if (account.equals( m_selAcct) ) {
			if (accountSummaryTag == AccountSummaryTag.NetLiquidation) {
				netLiquidationTextField.setText(doubleZeroFormat.format(Double.parseDouble(value)));
				m_lastUpdated.setText("Last Updated: " + new Date());
			} else if (accountSummaryTag == AccountSummaryTag.ExcessLiquidity) {
			    excessLiquidity = Double.parseDouble(value);
            } else if (accountSummaryTag == AccountSummaryTag.TotalCashValue) {
			    totalCashValue = Double.parseDouble(value);
            } else if (accountSummaryTag == AccountSummaryTag.BuyingPower) {
			    buyingPower = Double.parseDouble(value);
            }
		}
	}

	@Override
	public void accountSummaryEnd() {

	}
	
	//---------- Display Groups -------
	@Override
	public void displayGroupList(int[] groups) {
		// groups are sorted most used to least used
		if (groups.length > 0) {
			// subscribe to the most used group
			MainPanel.INSTANCE.controller().subscribeToGroupEvents(groups[0], this);
		}
	}
	
	@Override
	public void displayGroupUpdated(String contractInfo) {
		String CONTRACT_INFO_SEPARATOR = "@";
		String[] split = contractInfo.split(CONTRACT_INFO_SEPARATOR);
		if (split.length > 0) {
			int id = Integer.parseInt(split[0]);
			String exchange = "SMART";
			if (split.length == 2) {
				exchange = split[1];
			}
			NewContract contract = new NewContract();
			contract.exchange(exchange);
			contract.conid(id);
			
			// before we request the data, we should clear the current price and stop loss fields so it's not confusing
            // if we don't get anything back for the current contract price
            currentPriceTextField.setText("");
            stopLossTextField.setText("");
            
			MainPanel.INSTANCE.controller().reqTopMktData(contract, "", true, this);
			MainPanel.INSTANCE.controller().reqContractDetails(contract, this);
		}
	}

	// -------------------- Top of Market Data ------------------
	@Override
	public void tickPrice(NewTickType tickType, double price, int canAutoExecute) {
		if (tickType == NewTickType.LAST) {
			updatePrice(price);
		} else if (tickType == NewTickType.CLOSE) {
			// TODO revisit this during market hours
			if (currentPriceTextField.getText().isEmpty()) {
				updatePrice(price);
			}
		}
	}
	
	private void updatePrice(double price) {
		String strPrice = doubleZeroFormat.format(price);
		currentPriceTextField.setText(strPrice);
		stopLossTextField.setText(strPrice);
		m_lastUpdated.setText("Last Updated: " + new Date());
	}

	@Override
	public void tickSize(NewTickType tickType, int size) {

	}

	@Override
	public void tickString(NewTickType tickType, String value) {

	}

	@Override
	public void tickSnapshotEnd() {

	}

	@Override
	public void marketDataType(Types.MktDataType marketDataType) {

	}

	// ------------------- Contract Details -------------------
	@Override
	public void contractDetails(ArrayList<NewContractDetails> list) {
		for (NewContractDetails details : list) {
			String symbol = details.contract().symbol();
			currentContractTextField.setText(symbol);
		}
	}
	
	private void validateValues(double nlv, double currentPrice, double risk, double stopLoss) throws InputMismatchException {
		// TODO make this more robust, and display meaningful messages
		if (nlv <= 0 || currentPrice <= 0 || currentPrice >= nlv || risk <= 0 || risk > 100 || stopLoss == currentPrice) {
			throw new InputMismatchException();
		}
	}
}
