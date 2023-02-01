# Dailies
A background service application that performs various automated tasks on a scheduled basis

Currently has the following tasks available:

## YahooFinanceTask
This task will query the Yahoo Finance API, retrieve information about the provided tickers, and generate a word document with a given template by replacing the following pre-determined tokens:
```
{company_name} = Replaced with the name of the company
{ticker} = Replaced with the ticker of the company
{stock_price} = Replaced with the stock price of the company
{market_cap} = Replaced with the market cap of the company
{52_week_low} = Replaced with the year low of the company
{52_week_high} = Replaced with the year high of the company
{average_volume} = Replaced with the average volume of the company
{scrape_date} = Replaced with the date at which this information was retrieved (when this task is run)
{headline_title_###} = Replaced with the title of the # (1st, 2nd, etc.) headline for the company
{headline_link_###} = Replaced with the link of the # (1st, 2nd, etc.) headline for the company
```

## DiscordChatExporterTask
This task will use the **CLI version** of [DiscordChatExporter](https://github.com/Tyrrrz/DiscordChatExporter) to export chats from Discord to a file.
