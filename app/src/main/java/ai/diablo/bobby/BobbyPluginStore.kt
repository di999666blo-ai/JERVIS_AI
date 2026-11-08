package ai.diablo.bobby

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BobbyPluginDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val category: String = "General",
    val authType: String = "Provider login/API key",
    val readiness: String = "CATALOGUE"
)

/** Searchable provider directory. Catalogue entries are not claims of working adapters. */
class BobbyPluginStore(context: Context) {
    private val prefs = context.getSharedPreferences("bobby_plugin_registry", Context.MODE_PRIVATE)
    private val vault = BobbySecretVault(context)

    val catalog = listOf(
        BobbyPluginDescriptor("github", "GitHub", "Secure token storage exists; repository and Actions API operations are not yet wired.", "Developer", "Fine-grained token", "FOUNDATION"),
        BobbyPluginDescriptor("deriv", "Deriv", "Account verification only; live market streaming and trading are not implemented.", "Trading", "API token", "READY"),
        BobbyPluginDescriptor("web_research", "Web Research", "Search and page reading adapter pending.", "Research"),
        BobbyPluginDescriptor("google_drive", "Google Drive", "Cloud file search and OAuth adapter pending.", "Storage"),
        BobbyPluginDescriptor("notion","Notion","Pages, databases and workspace content.","Productivity"),
        BobbyPluginDescriptor("slack","Slack","Messages, channels and workspace search.","Communication"),
        BobbyPluginDescriptor("discord","Discord","Servers, channels and bot-authorised messages.","Communication"),
        BobbyPluginDescriptor("telegram","Telegram","Bot messaging and channel workflows.","Communication"),
        BobbyPluginDescriptor("whatsapp_business","WhatsApp Business","Business messaging through approved Meta APIs.","Communication"),
        BobbyPluginDescriptor("gmail","Gmail","Email search, drafts and message actions through OAuth.","Email"),
        BobbyPluginDescriptor("outlook","Microsoft Outlook","Mail, folders and calendar-linked email workflows.","Email"),
        BobbyPluginDescriptor("microsoft_365","Microsoft 365","Microsoft account and Office services.","Productivity"),
        BobbyPluginDescriptor("google_calendar","Google Calendar","Events, schedules and availability.","Productivity"),
        BobbyPluginDescriptor("google_contacts","Google Contacts","Contact lookup and management.","Productivity"),
        BobbyPluginDescriptor("google_sheets","Google Sheets","Spreadsheet reading and updates.","Productivity"),
        BobbyPluginDescriptor("google_docs","Google Docs","Document search, reading and editing.","Productivity"),
        BobbyPluginDescriptor("google_slides","Google Slides","Presentation and slide workflows.","Productivity"),
        BobbyPluginDescriptor("google_tasks","Google Tasks","Task lists and task updates.","Productivity"),
        BobbyPluginDescriptor("google_maps","Google Maps","Places, directions and map information.","Travel"),
        BobbyPluginDescriptor("google_photos","Google Photos","Photo library access through supported APIs.","Media"),
        BobbyPluginDescriptor("dropbox","Dropbox","Cloud file search and storage.","Storage"),
        BobbyPluginDescriptor("onedrive","OneDrive","Microsoft cloud file access.","Storage"),
        BobbyPluginDescriptor("box","Box","Enterprise file storage and collaboration.","Storage"),
        BobbyPluginDescriptor("sharepoint","SharePoint","Sites, lists and document libraries.","Storage"),
        BobbyPluginDescriptor("evernote","Evernote","Notes, notebooks and search.","Productivity"),
        BobbyPluginDescriptor("obsidian","Obsidian","Local vault workflows through approved integrations.","Productivity"),
        BobbyPluginDescriptor("onenote","OneNote","Notes and notebooks.","Productivity"),
        BobbyPluginDescriptor("todoist","Todoist","Tasks, projects and due dates.","Productivity"),
        BobbyPluginDescriptor("ticktick","TickTick","Tasks, habits and reminders.","Productivity"),
        BobbyPluginDescriptor("trello","Trello","Boards, cards and checklists.","Productivity"),
        BobbyPluginDescriptor("asana","Asana","Projects, tasks and team workflows.","Productivity"),
        BobbyPluginDescriptor("monday","Monday.com","Boards, items and work management.","Productivity"),
        BobbyPluginDescriptor("clickup","ClickUp","Tasks, docs and project tracking.","Productivity"),
        BobbyPluginDescriptor("linear","Linear","Issues, projects and cycles.","Developer"),
        BobbyPluginDescriptor("jira","Jira","Issues, sprints and project management.","Developer"),
        BobbyPluginDescriptor("confluence","Confluence","Team knowledge and documentation.","Productivity"),
        BobbyPluginDescriptor("airtable","Airtable","Bases, records and automations.","Productivity"),
        BobbyPluginDescriptor("coda","Coda","Docs, tables and workflows.","Productivity"),
        BobbyPluginDescriptor("hubspot","HubSpot","CRM, contacts and marketing workflows.","Business"),
        BobbyPluginDescriptor("salesforce","Salesforce","CRM records and sales operations.","Business"),
        BobbyPluginDescriptor("pipedrive","Pipedrive","Sales pipeline and deal management.","Business"),
        BobbyPluginDescriptor("zoho_crm","Zoho CRM","CRM and customer workflows.","Business"),
        BobbyPluginDescriptor("freshdesk","Freshdesk","Customer support tickets.","Business"),
        BobbyPluginDescriptor("zendesk","Zendesk","Support tickets and customer service.","Business"),
        BobbyPluginDescriptor("intercom","Intercom","Customer conversations and support.","Business"),
        BobbyPluginDescriptor("shopify","Shopify","Store, products and order management.","Commerce"),
        BobbyPluginDescriptor("woocommerce","WooCommerce","WordPress store and order workflows.","Commerce"),
        BobbyPluginDescriptor("stripe","Stripe","Payment and billing records; sensitive actions need approval.","Payments"),
        BobbyPluginDescriptor("paypal","PayPal","Account and transaction workflows where API permits.","Payments"),
        BobbyPluginDescriptor("square","Square","Payments, orders and point-of-sale data.","Payments"),
        BobbyPluginDescriptor("quickbooks","QuickBooks","Accounting records and invoices.","Finance"),
        BobbyPluginDescriptor("xero","Xero","Accounting and bookkeeping workflows.","Finance"),
        BobbyPluginDescriptor("plaid","Plaid","Authorised financial account data access.","Finance"),
        BobbyPluginDescriptor("coinbase","Coinbase","Account and market data through authorised API.","Crypto"),
        BobbyPluginDescriptor("binance","Binance","Exchange data and trading API; trading disabled by default.","Crypto"),
        BobbyPluginDescriptor("kraken","Kraken","Exchange balances and market data.","Crypto"),
        BobbyPluginDescriptor("bybit","Bybit","Derivatives exchange data; live orders require safeguards.","Crypto"),
        BobbyPluginDescriptor("tradingview","TradingView","Chart alerts and supported integrations.","Trading"),
        BobbyPluginDescriptor("metatrader","MetaTrader","Trading platform bridge where broker/API permits.","Trading"),
        BobbyPluginDescriptor("quotex","Quotex","No official public trading API confirmed; no adapter claimed.","Trading"),
        BobbyPluginDescriptor("twelve_data","Twelve Data","Market prices and historical data.","Trading"),
        BobbyPluginDescriptor("alpha_vantage","Alpha Vantage","Market data and technical indicators.","Trading"),
        BobbyPluginDescriptor("polygon","Polygon.io","Market data and aggregates.","Trading"),
        BobbyPluginDescriptor("finnhub","Finnhub","Stocks, forex, crypto and news data.","Trading"),
        BobbyPluginDescriptor("coingecko","CoinGecko","Cryptocurrency prices and market information.","Trading"),
        BobbyPluginDescriptor("coinmarketcap","CoinMarketCap","Crypto market data and rankings.","Trading"),
        BobbyPluginDescriptor("newsapi","NewsAPI","News search and article metadata.","Research"),
        BobbyPluginDescriptor("brave_search","Brave Search","Web search API.","Research"),
        BobbyPluginDescriptor("tavily","Tavily","Search API designed for AI research.","Research"),
        BobbyPluginDescriptor("serpapi","SerpApi","Search engine results API.","Research"),
        BobbyPluginDescriptor("exa","Exa","Semantic web search and content discovery.","Research"),
        BobbyPluginDescriptor("firecrawl","Firecrawl","Website crawling and page extraction.","Research"),
        BobbyPluginDescriptor("youtube","YouTube","Video search and metadata through supported APIs.","Media"),
        BobbyPluginDescriptor("vimeo","Vimeo","Video library and metadata.","Media"),
        BobbyPluginDescriptor("spotify","Spotify","Music search and playback control where authorised.","Media"),
        BobbyPluginDescriptor("soundcloud","SoundCloud","Audio discovery and library access.","Media"),
        BobbyPluginDescriptor("twitch","Twitch","Streams, channels and chat integrations.","Media"),
        BobbyPluginDescriptor("reddit","Reddit","Subreddit search and authorised account actions.","Social"),
        BobbyPluginDescriptor("x","X","Posts and account workflows subject to API access.","Social"),
        BobbyPluginDescriptor("facebook","Facebook","Pages and account integrations through Meta APIs.","Social"),
        BobbyPluginDescriptor("instagram","Instagram","Professional account publishing through approved APIs.","Social"),
        BobbyPluginDescriptor("linkedin","LinkedIn","Professional profile and page workflows.","Social"),
        BobbyPluginDescriptor("pinterest","Pinterest","Boards and pins through supported APIs.","Social"),
        BobbyPluginDescriptor("mastodon","Mastodon","Federated social posts and timelines.","Social"),
        BobbyPluginDescriptor("bluesky","Bluesky","Social feed and posting APIs.","Social"),
        BobbyPluginDescriptor("wordpress","WordPress","Site content and publishing.","Publishing"),
        BobbyPluginDescriptor("ghost","Ghost","Publication and membership content.","Publishing"),
        BobbyPluginDescriptor("webflow","Webflow","CMS content and site workflows.","Publishing"),
        BobbyPluginDescriptor("wix","Wix","Site and business APIs.","Publishing"),
        BobbyPluginDescriptor("canva","Canva","Design assets and supported export workflows.","Design"),
        BobbyPluginDescriptor("figma","Figma","Design files, comments and collaboration.","Design"),
        BobbyPluginDescriptor("cloudinary","Cloudinary","Image and video asset management.","Media"),
        BobbyPluginDescriptor("openai","OpenAI","AI model API and speech services.","AI Models"),
        BobbyPluginDescriptor("anthropic","Anthropic","Claude model API.","AI Models"),
        BobbyPluginDescriptor("google_gemini","Google Gemini","Gemini model API and multimodal services.","AI Models"),
        BobbyPluginDescriptor("mistral","Mistral AI","Mistral model API.","AI Models"),
        BobbyPluginDescriptor("cohere","Cohere","Language and embedding API.","AI Models"),
        BobbyPluginDescriptor("groq","Groq","Inference API for supported models.","AI Models"),
        BobbyPluginDescriptor("together_ai","Together AI","Hosted open model inference.","AI Models"),
        BobbyPluginDescriptor("hugging_face","Hugging Face","Model and inference endpoints.","AI Models"),
        BobbyPluginDescriptor("replicate","Replicate","Hosted model execution.","AI Models"),
        BobbyPluginDescriptor("perplexity","Perplexity","AI search and answer API.","AI Models"),
        BobbyPluginDescriptor("elevenlabs","ElevenLabs","Speech synthesis and voice APIs.","AI Models"),
        BobbyPluginDescriptor("deepgram","Deepgram","Speech recognition and audio APIs.","AI Models"),
        BobbyPluginDescriptor("assemblyai","AssemblyAI","Audio transcription and understanding.","AI Models"),
        BobbyPluginDescriptor("aws","Amazon Web Services","AWS service workflows through scoped credentials.","Developer"),
        BobbyPluginDescriptor("azure","Microsoft Azure","Cloud resources and identity workflows.","Developer"),
        BobbyPluginDescriptor("vercel","Vercel","Deployments, projects and domains.","Developer"),
        BobbyPluginDescriptor("netlify","Netlify","Site deployments and build status.","Developer"),
        BobbyPluginDescriptor("render","Render","Application services and deployment status.","Developer"),
        BobbyPluginDescriptor("railway","Railway","Project and deployment workflows.","Developer"),
        BobbyPluginDescriptor("cloudflare","Cloudflare","DNS, security and edge services.","Developer"),
        BobbyPluginDescriptor("supabase","Supabase","Database, auth and storage APIs.","Developer"),
        BobbyPluginDescriptor("firebase","Firebase","App backend, analytics and messaging.","Developer"),
        BobbyPluginDescriptor("appwrite","Appwrite","Backend services and storage.","Developer"),
        BobbyPluginDescriptor("mongodb","MongoDB Atlas","Database and cluster management.","Developer"),
        BobbyPluginDescriptor("sentry","Sentry","Error monitoring and issue tracking.","Developer"),
        BobbyPluginDescriptor("datadog","Datadog","Infrastructure and application monitoring.","Developer"),
        BobbyPluginDescriptor("gitlab","GitLab","Repositories, merge requests and CI pipelines.","Developer"),
        BobbyPluginDescriptor("bitbucket","Bitbucket","Repositories and pull requests.","Developer"),
        BobbyPluginDescriptor("zapier","Zapier","Connect supported apps through Zapier.","Automation"),
        BobbyPluginDescriptor("make","Make","Visual workflow automation.","Automation"),
        BobbyPluginDescriptor("n8n","n8n","Self-hosted workflow automation.","Automation"),
        BobbyPluginDescriptor("ifttt","IFTTT","Consumer device and app automations.","Automation"),
        BobbyPluginDescriptor("home_assistant","Home Assistant","Local smart-home control with user permission.","Smart Home"),
        BobbyPluginDescriptor("smartthings","SmartThings","Samsung smart-home device workflows.","Smart Home"),
        BobbyPluginDescriptor("philips_hue","Philips Hue","Lighting scenes and device control.","Smart Home"),
        BobbyPluginDescriptor("fitbit","Fitbit","Activity and wellness data through authorised APIs.","Health"),
        BobbyPluginDescriptor("strava","Strava","Fitness activities and training data.","Health"),
        BobbyPluginDescriptor("oura","Oura","Sleep and readiness data through authorised APIs.","Health"),
        BobbyPluginDescriptor("zoom","Zoom","Meetings and webinar workflows.","Communication"),
        BobbyPluginDescriptor("microsoft_teams","Microsoft Teams","Teams messages and meeting workflows.","Communication"),
        BobbyPluginDescriptor("webex","Webex","Meetings and messaging.","Communication"),
        BobbyPluginDescriptor("twilio","Twilio","Programmable messaging and voice.","Communication"),
        BobbyPluginDescriptor("sendgrid","SendGrid","Transactional email API.","Email"),
        BobbyPluginDescriptor("mailchimp","Mailchimp","Email campaigns and audience data.","Marketing"),
        BobbyPluginDescriptor("brevo","Brevo","Email and customer communication.","Marketing"),
        BobbyPluginDescriptor("typeform","Typeform","Forms, responses and surveys.","Productivity"),
        BobbyPluginDescriptor("jotform","Jotform","Forms and submission data.","Productivity"),
        BobbyPluginDescriptor("calendly","Calendly","Scheduling links and event data.","Productivity"),
        BobbyPluginDescriptor("docusign","DocuSign","Document signature workflows.","Business")
    )

    fun search(query: String = "", category: String = ""): List<BobbyPluginDescriptor> {
        val q = query.trim().lowercase()
        return catalog.filter { item ->
            (q.isBlank() || item.name.lowercase().contains(q) || item.description.lowercase().contains(q) || item.category.lowercase().contains(q)) &&
            (category.isBlank() || item.category.equals(category, true))
        }
    }

    fun categories(): List<String> = catalog.map { it.category }.distinct().sorted()
    fun isEnabled(id: String): Boolean = prefs.getBoolean("enabled_$id", false)
    fun setEnabled(id: String, enabled: Boolean) {
        require(catalog.any { it.id == id } || id.matches(Regex("[a-z0-9_]{1,48}")))
        prefs.edit().putBoolean("enabled_$id", enabled).apply()
    }
    fun saveGitHubToken(token: String) {
        require(token.trim().isNotEmpty()) { "Enter a GitHub fine-grained token." }
        require(token.length <= 500) { "Token is too long." }
        vault.put("github_pat", token.trim())
    }
    fun hasGitHubToken(): Boolean = vault.get("github_pat").isNotBlank()
    fun removeGitHubToken() = vault.remove("github_pat")
    fun addCustom(name: String, baseUrl: String) {
        val cleanName = name.trim()
        val cleanUrl = baseUrl.trim()
        require(cleanName.length in 2..40) { "Connector name must be 2–40 characters." }
        require(cleanUrl.startsWith("https://", ignoreCase = true)) { "Only HTTPS connector URLs are allowed." }
        require(android.net.Uri.parse(cleanUrl).host?.isNotBlank() == true) { "Enter a valid HTTPS URL." }
        val rows = JSONArray(prefs.getString("custom_connectors", "[]"))
        require(rows.length() < 20) { "The custom connector list is full." }
        require((0 until rows.length()).none { rows.getJSONObject(it).optString("name").equals(cleanName, true) }) { "A connector with that name already exists." }
        rows.put(JSONObject().put("name", cleanName).put("url", cleanUrl))
        prefs.edit().putString("custom_connectors", rows.toString()).apply()
    }
    fun customConnectors(): List<Pair<String, String>> {
        val rows = JSONArray(prefs.getString("custom_connectors", "[]"))
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> row.optString("name") to row.optString("url") } }
    }
}