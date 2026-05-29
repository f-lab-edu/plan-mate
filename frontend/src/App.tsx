import './App.css'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api'

const navItems = ['Trips', 'Stays', 'Itinerary', 'Chat']

const itinerary = [
  { time: '09:00', title: 'Hotel checkout', detail: 'Fixed by host', type: 'fixed' },
  { time: '10:20', title: 'Seongsu cafe route', detail: 'AI recommendation', type: 'ai' },
  { time: '12:00', title: 'Market lunch', detail: 'Budget friendly', type: 'ai' },
  { time: '15:30', title: 'Move to Gangneung stay', detail: 'Transit time included', type: 'move' },
]

const stays = [
  { day: 'Day 1-2', name: 'Seoul hotel', area: 'Seongsu' },
  { day: 'Day 3-5', name: 'Gangneung stay', area: 'Anmok beach' },
]

function App() {
  return (
    <main className="app-shell">
      <aside className="sidebar" aria-label="Primary navigation">
        <div className="brand">
          <img src="/favicon.svg" alt="" aria-hidden="true" />
          <div>
            <strong>PlanMate</strong>
            <span>Trip workspace</span>
          </div>
        </div>

        <nav className="nav-list">
          {navItems.map((item, index) => (
            <button key={item} className={index === 0 ? 'nav-item active' : 'nav-item'} type="button">
              <span className="nav-mark" aria-hidden="true" />
              {item}
            </button>
          ))}
        </nav>

        <div className="api-status">
          <span>API base</span>
          <code>{apiBaseUrl}</code>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <p className="eyebrow">MVP 1 workspace</p>
            <h1>Build an executable trip plan</h1>
          </div>
          <button className="primary-button" type="button">
            New trip
          </button>
        </header>

        <div className="workspace-grid">
          <section className="panel trip-panel" aria-labelledby="trip-title">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Trip draft</p>
                <h2 id="trip-title">Seoul to Gangneung</h2>
              </div>
              <span className="status-pill">Planning</span>
            </div>

            <form className="trip-form">
              <label>
                Destination
                <input type="text" value="Seoul, Gangneung" readOnly />
              </label>
              <label>
                Dates
                <input type="text" value="Jun 12 - Jun 16" readOnly />
              </label>
              <label>
                Budget
                <input type="text" value="KRW 850,000" readOnly />
              </label>
              <label>
                Travel style
                <select defaultValue="balanced">
                  <option value="balanced">Balanced</option>
                  <option value="food">Food focused</option>
                  <option value="quiet">Quiet route</option>
                </select>
              </label>
            </form>

            <div className="transport-options" aria-label="Transportation options">
              <button className="option-button selected" type="button">
                Car
              </button>
              <button className="option-button" type="button">
                Transit
              </button>
            </div>
          </section>

          <section className="panel map-panel" aria-label="Route overview">
            <div className="route-map">
              <div className="map-node origin">Seoul</div>
              <div className="map-path" />
              <div className="map-node stop">Cafe</div>
              <div className="map-path accent" />
              <div className="map-node destination">Gangneung</div>
            </div>
            <div className="route-meta">
              <div>
                <span>Drive time</span>
                <strong>2h 42m</strong>
              </div>
              <div>
                <span>Parking checks</span>
                <strong>3 places</strong>
              </div>
            </div>
          </section>

          <section className="panel stays-panel" aria-labelledby="stays-title">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Multi accommodation</p>
                <h2 id="stays-title">Registered stays</h2>
              </div>
            </div>

            <div className="stay-list">
              {stays.map((stay) => (
                <article className="stay-item" key={stay.name}>
                  <span>{stay.day}</span>
                  <strong>{stay.name}</strong>
                  <p>{stay.area}</p>
                </article>
              ))}
            </div>
          </section>

          <section className="panel itinerary-panel" aria-labelledby="itinerary-title">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">AI itinerary</p>
                <h2 id="itinerary-title">Day 2 schedule</h2>
              </div>
              <button className="secondary-button" type="button">
                Regenerate
              </button>
            </div>

            <ol className="timeline">
              {itinerary.map((slot) => (
                <li key={`${slot.time}-${slot.title}`} className={`timeline-item ${slot.type}`}>
                  <time>{slot.time}</time>
                  <div>
                    <strong>{slot.title}</strong>
                    <span>{slot.detail}</span>
                  </div>
                </li>
              ))}
            </ol>
          </section>

          <section className="panel collaboration-panel" aria-labelledby="collaboration-title">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">Collaboration</p>
                <h2 id="collaboration-title">Travel group</h2>
              </div>
            </div>

            <div className="avatar-row" aria-label="Collaborators">
              <span>HJ</span>
              <span>MK</span>
              <span>SY</span>
              <button className="avatar-button" type="button" aria-label="Invite member">
                +
              </button>
            </div>

            <div className="vote-box">
              <div>
                <strong>Lunch route vote</strong>
                <span>2 of 3 approved</span>
              </div>
              <span className="checkmark" aria-hidden="true">
                OK
              </span>
            </div>

            <div className="chat-preview">
              <strong>Latest chat</strong>
              <p>Move the cafe slot after checkout.</p>
            </div>
          </section>
        </div>
      </section>
    </main>
  )
}

export default App
