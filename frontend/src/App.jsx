import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import "./App.css";

// Backend direct URL to avoid dev proxy/port mismatch
const API_URL = "/api/leaves";

// If your backend has Basic Auth
const USE_AUTH = true;
const AUTH = { username: "admin", password: "admin123" };
const AUTH_URL = "/api/auth";

export default function App() {
  const [leaves, setLeaves] = useState([]);
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState({ type: "", text: "" });
  const [currentPage, setCurrentPage] = useState("dashboard");

  const [credentials, setCredentials] = useState({ username: "", password: "", role: "EMPLOYEE" });
  const [registerData, setRegisterData] = useState({ username: "", password: "", confirmPassword: "" });
  const [loggedIn, setLoggedIn] = useState(false);
  const [showRegister, setShowRegister] = useState(false);

  const [formData, setFormData] = useState({
    employeeName: "",
    leaveType: "CASUAL",
    startDate: "",
    endDate: "",
    reason: "",
  });

  const [chatMessages, setChatMessages] = useState([]);
  const [chatInput, setChatInput] = useState("");
  const [aiInsights, setAiInsights] = useState({});
  const [aiPrediction, setAiPrediction] = useState(null);

  // Fetch leaves when logged in
  useEffect(() => {
    if (loggedIn) {
      fetchLeaves();
    }
  }, [loggedIn]);

  // Fetch leaves when navigating to leaves page
  useEffect(() => {
    if (loggedIn && currentPage === "leaves") {
      fetchLeaves();
    }
  }, [currentPage]);

  // Fetch AI insights when navigating to insights page
  useEffect(() => {
    if (loggedIn && currentPage === "insights") {
      fetchAiInsights();
    }
  }, [currentPage, loggedIn]);

  const getAuthConfig = () => ({
    headers: { "Content-Type": "application/json" },
    auth: {
      username: credentials.username,
      password: credentials.password,
    },
  });

  const toast = (type, text) => {
    setMsg({ type, text });
    setTimeout(() => setMsg({ type: "", text: "" }), 2500);
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    if (!credentials.username || !credentials.password) {
      return toast("error", "Enter username and password");
    }

    try {
      setLoading(true);
      const endpoint = credentials.role === "ADMIN" ? "" : "/me";
      await axios.get(`${API_URL}${endpoint}`, getAuthConfig());
      setLoggedIn(true);
      toast("success", `Logged in as ${credentials.role}`);
      await fetchLeaves();
    } catch (e) {
      toast("error", "Authentication failed");
      setLoggedIn(false);
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    if (!registerData.username || !registerData.password || !registerData.confirmPassword) {
      return toast("error", "All fields are required for registration");
    }
    if (registerData.password !== registerData.confirmPassword) {
      return toast("error", "Passwords do not match");
    }

    try {
      setLoading(true);
      await axios.post(`${AUTH_URL}/register`, {
        username: registerData.username,
        password: registerData.password,
      });
      toast("success", "Registration successful, logging you in...");
      setCredentials({ username: registerData.username, password: registerData.password, role: "EMPLOYEE" });
      setRegisterData({ username: "", password: "", confirmPassword: "" });
      setLoggedIn(true);
      await fetchLeaves();
    } catch (e) {
      toast("error", e?.response?.data?.message || "Registration failed");
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = () => {
    setLoggedIn(false);
    setLeaves([]);
    setCurrentPage("dashboard");
    setCredentials({ username: "", password: "", role: "EMPLOYEE" });
    toast("success", "Logged out");
  };

  const fetchLeaves = async () => {
    if (!loggedIn) return;

    try {
      setLoading(true);
      const endpoint = credentials.role === "ADMIN" ? "" : "/me";
      const res = await axios.get(`${API_URL}${endpoint}`, getAuthConfig());
      setLeaves(Array.isArray(res.data) ? res.data : []);
    } catch (e) {
      toast("error", e?.response?.data?.message || "Failed to load leaves");
      if (e?.response?.status === 401) {
        setLoggedIn(false);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLeaves();
  }, [loggedIn, credentials.role]);

  const handleChange = (e) => {
    setFormData((p) => ({ ...p, [e.target.name]: e.target.value }));
  };

  const validDates = () => {
    if (!formData.startDate || !formData.endDate) return true;
    return new Date(formData.endDate) >= new Date(formData.startDate);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!loggedIn) return toast("error", "Login first");
    if (!validDates()) return toast("error", "End date must be after start date");

    try {
      setLoading(true);
      const payload = { ...formData };
      if (credentials.role === "EMPLOYEE") {
        payload.employeeName = credentials.username;
      }
      await axios.post(API_URL, payload, getAuthConfig());
      toast("success", "Leave applied ✅");

      setFormData({
        employeeName: "",
        leaveType: "CASUAL",
        startDate: "",
        endDate: "",
        reason: "",
      });

      await fetchLeaves();
    } catch (e) {
      toast("error", e?.response?.data?.message || "Apply failed");
    } finally {
      setLoading(false);
    }
  };

  const deleteLeave = async (id) => {
    if (!confirm(`Delete leave id ${id}?`)) return;

    try {
      setLoading(true);
      await axios.delete(`${API_URL}/${id}`, getAuthConfig());
      toast("success", "Deleted ✅");
      await fetchLeaves();
    } catch (e) {
      toast("error", e?.response?.data?.message || "Delete failed");
    } finally {
      setLoading(false);
    }
  };

  const updateStatus = async (leave, status) => {
    try {
      setLoading(true);

      // ✅ IMPORTANT: send full record + updated status
      // so backend will not overwrite other fields with null
      const payload = {
        employeeName: leave.employeeName,
        leaveType: leave.leaveType,
        startDate: leave.startDate,
        endDate: leave.endDate,
        reason: leave.reason,
        status: status,
      };

      await axios.put(`${API_URL}/${leave.id}`, payload, getAuthConfig());

      toast("success", `Marked ${status} ✅`);
      await fetchLeaves();
    } catch (e) {
      toast("error", e?.response?.data?.message || "Update failed");
    } finally {
      setLoading(false);
    }
  };

  const sendChatMessage = async (e) => {
    e.preventDefault();
    if (!chatInput.trim()) return;

    const userMessage = { role: "user", content: chatInput };
    setChatMessages(prev => [...prev, userMessage]);
    setChatInput("");

    try {
      const response = await axios.post(`${API_URL}/chat`, { message: chatInput }, getAuthConfig());
      const aiMessage = { role: "ai", content: response.data };
      setChatMessages(prev => [...prev, aiMessage]);
    } catch (e) {
      const errorMessage = { role: "ai", content: "Sorry, I couldn't process your request." };
      setChatMessages(prev => [...prev, errorMessage]);
    }
  };

  const fetchAiInsights = async () => {
    try {
      setLoading(true);
      const [trendsRes, teamAlertsRes, hrAlertsRes] = await Promise.all([
        axios.get(`${API_URL}/trends`, getAuthConfig()),
        axios.get(`${API_URL}/team-alerts`, getAuthConfig()),
        axios.get(`${API_URL}/hr-alerts`, getAuthConfig())
      ]);

      setAiInsights({
        trends: trendsRes.data,
        teamAlerts: teamAlertsRes.data,
        hrAlerts: hrAlertsRes.data
      });
    } catch (e) {
      toast("error", "Failed to load AI insights");
    } finally {
      setLoading(false);
    }
  };

  const getLeavePrediction = async (leave) => {
    try {
      const response = await axios.post(`${API_URL}/predict-detailed`, leave, getAuthConfig());
      return response.data;
    } catch (e) {
      return { recommendation: "Unable to analyze" };
    }
  };

  const showAIPrediction = async (leave) => {
    try {
      setLoading(true);
      const prediction = await getLeavePrediction(leave);
      setAiPrediction({ leave, prediction });
    } catch (e) {
      toast("error", "Failed to get AI prediction");
    } finally {
      setLoading(false);
    }
  };

  const closeAIPrediction = () => {
    setAiPrediction(null);
  };

  const handleBack = () => {
    if (!loggedIn) return;
    if (currentPage === "dashboard") return;
    setCurrentPage("dashboard");
  };

  const isPending = (leave) => String(leave.status || "Pending").toLowerCase() === "pending";

  const leaveStats = {
    total: leaves.length,
    approved: leaves.filter((l) => String(l.status || "Pending").toLowerCase() === "approved").length,
    rejected: leaves.filter((l) => String(l.status || "Pending").toLowerCase() === "rejected").length,
    pending: leaves.filter((l) => String(l.status || "Pending").toLowerCase() === "pending").length,
  };

  return (
    <div className="page">
      <div className="main-container">
      {msg.text && (
        <div className={`toast ${msg.type === "success" ? "toast-success" : "toast-error"}`}>
          {msg.text}
        </div>
      )}

      {!loggedIn ? (
        <div className="login-wrapper">
          <div className="login-container">
            <div className="login-header">
              <h1>Employee Leave Management</h1>
              <p>Fast, secure, AI-powered leave tracking for employees and admins.</p>
            </div>
            <div className="login-grid">
              <section className="card">
                {showRegister ? (
                  <>
                    <h2>New Employee Register</h2>
                    <form className="form" onSubmit={handleRegister}>
                      <label>
                        Username
                        <input
                          type="text"
                          value={registerData.username}
                          onChange={(e) => setRegisterData((r) => ({ ...r, username: e.target.value }))}
                          required
                        />
                      </label>

                      <label>
                        Password
                        <input
                          type="password"
                          value={registerData.password}
                          onChange={(e) => setRegisterData((r) => ({ ...r, password: e.target.value }))}
                          required
                        />
                      </label>

                      <label>
                        Confirm Password
                        <input
                          type="password"
                          value={registerData.confirmPassword}
                          onChange={(e) => setRegisterData((r) => ({ ...r, confirmPassword: e.target.value }))}
                          required
                        />
                      </label>

                      <button className="btn btn-primary" type="submit" disabled={loading}>
                        Register
                      </button>
                    </form>
                    <button className="btn btn-light" onClick={() => setShowRegister(false)}>
                      Back to Login
                    </button>
                  </>
                ) : (
                  <>
                    <h2>Login</h2>
                    <form className="form" onSubmit={handleLogin}>
                      <label>
                        Role
                        <select
                          value={credentials.role}
                          onChange={(e) => setCredentials((c) => ({ ...c, role: e.target.value }))}
                        >
                          <option value="EMPLOYEE">Employee</option>
                          <option value="ADMIN">Admin</option>
                        </select>
                      </label>

                      <label>
                        Username
                        <input
                          type="text"
                          value={credentials.username}
                          onChange={(e) => setCredentials((c) => ({ ...c, username: e.target.value }))}
                          required
                        />
                      </label>

                      <label>
                        Password
                        <input
                          type="password"
                          value={credentials.password}
                          onChange={(e) => setCredentials((c) => ({ ...c, password: e.target.value }))}
                          required
                        />
                      </label>

                      <button className="btn btn-primary" type="submit" disabled={loading}>
                        Login
                      </button>
                    </form>

                    <p className="muted">
                      Demo: admin/admin123, testuser/testpass
                    </p>
                    <button className="btn btn-outline" onClick={() => setShowRegister(true)}>
                      New Employee? Register Here
                    </button>
                  </>
                )}
              </section>
            </div>
          </div>
        </div>
      ) : currentPage === "dashboard" ? (
        <div className="container dashboard-container">
          <header className="header">
            <div className="header-left">
              <button className="btn btn-back" onClick={handleBack} disabled={currentPage === "dashboard"}>
                ← Back
              </button>
              <div>
                <h1>Employee Leave Management</h1>
                <p>Your leave management portal</p>
              </div>
            </div>

            <button className="btn btn-light" onClick={handleLogout} disabled={loading}>
              Logout
            </button>
          </header>

          <div className="dashboard">
            <div className="dashboard-welcome">
              <div className="welcome-card">
                <h2>Welcome, {credentials.username}!</h2>
                <p className="role-badge">{credentials.role}</p>
                <p className="welcome-msg">
                  {credentials.role === "ADMIN" 
                    ? "You have admin access to manage all employee leave requests."
                    : "Manage your leave requests and check your leave balance."}
                </p>
              </div>
            </div>

            <div className="dashboard-grid">
              {credentials.role === "EMPLOYEE" ? (
                <>
                  <div className="dashboard-card quick-action">
                    <h3>📝 Apply for Leave</h3>
                    <p>Submit a new leave request</p>
                    <button 
                      className="btn btn-primary" 
                      onClick={() => setCurrentPage("leaves")}
                    >
                      Apply Now
                    </button>
                  </div>

                  <div className="dashboard-card quick-action">
                    <h3>📋 My Leave Requests</h3>
                    <p>View your leave history</p>
                    <button 
                      className="btn btn-primary" 
                      onClick={() => setCurrentPage("leaves")}
                    >
                      View Requests
                    </button>
                  </div>

                  <div className="dashboard-card quick-action">
                    <h3>🤖 AI Assistant</h3>
                    <p>Chat with AI for leave help</p>
                    <button 
                      className="btn btn-primary" 
                      onClick={() => setCurrentPage("chat")}
                    >
                      Start Chat
                    </button>
                  </div>

                  <div className="dashboard-card info">
                    <h3>💡 Quick Info</h3>
                    <ul>
                      <li>Submit leave requests in advance</li>
                      <li>Track request status in real-time</li>
                      <li>Stay informed with notifications</li>
                    </ul>
                  </div>
                </>
              ) : (
                <>
                  <div className="dashboard-card quick-action">
                    <h3>⚙️ Manage Leave Requests</h3>
                    <p>Approve or reject pending requests</p>
                    <button 
                      className="btn btn-primary" 
                      onClick={() => setCurrentPage("leaves")}
                    >
                      Go to Management
                    </button>
                  </div>

                  <div className="dashboard-card quick-action">
                    <h3>👥 View All Requests</h3>
                    <p>See all employee leave requests</p>
                    <button 
                      className="btn btn-primary" 
                      onClick={() => setCurrentPage("leaves")}
                    >
                      View All
                    </button>
                  </div>

                  <div className="dashboard-card quick-action">
                    <h3>🤖 AI Insights</h3>
                    <p>Advanced analytics & predictions</p>
                    <button 
                      className="btn btn-primary" 
                      onClick={() => setCurrentPage("insights")}
                    >
                      View Insights
                    </button>
                  </div>

                  <div className="dashboard-card info">
                    <h3>⚡ Admin Tools</h3>
                    <ul>
                      <li>Approve or reject requests</li>
                      <li>View all leave history</li>
                      <li>Manage company leave policy</li>
                    </ul>
                  </div>
                </>
              )}
            </div>

            <footer className="footer">
              <span>Admin Dashboard • Application running on http://localhost:8080</span>
            </footer>
          </div>
        </div>
      ) : currentPage === "chat" ? (
        <div className="container">
          <header className="header">
            <div className="header-left">
              <button className="btn btn-back" onClick={handleBack}>
                ← Back
              </button>
              <div>
                <h1>AI Leave Assistant</h1>
                <p>Chat with our AI for help with leave management</p>
              </div>
            </div>

            <button className="btn btn-light" onClick={handleBack}>
              Back to Dashboard
            </button>
          </header>

          <div className="auth-info">
            <div>
              <p>
                Logged in as <strong>{credentials.username}</strong> ({credentials.role})
              </p>
              <button className="btn btn-light" onClick={handleLogout} disabled={loading}>
                Logout
              </button>
            </div>
          </div>

          <div className="chat-container">
            <div className="chat-messages">
              {chatMessages.length === 0 && (
                <div className="chat-welcome">
                  <h3>👋 Hi! I'm your AI Leave Assistant</h3>
                  <p>Ask me about:</p>
                  <ul>
                    <li>How to apply for leave</li>
                    <li>Your leave balance</li>
                    <li>Leave history</li>
                    <li>Any leave-related questions</li>
                  </ul>
                </div>
              )}
              {chatMessages.map((msg, index) => (
                <div key={index} className={`chat-message ${msg.role}`}>
                  <div className="message-content">
                    {msg.content}
                  </div>
                </div>
              ))}
            </div>

            <form className="chat-input-form" onSubmit={sendChatMessage}>
              <input
                type="text"
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                placeholder="Type your message..."
                className="chat-input"
              />
              <button type="submit" className="btn btn-primary" disabled={loading}>
                Send
              </button>
            </form>
          </div>
        </div>
      ) : currentPage === "insights" ? (
        <div className="container insights-mode">
          <header className="header">
            <div className="header-left">
              <button className="btn btn-back" onClick={handleBack}>
                ← Back
              </button>
              <div>
                <h1>AI Insights Dashboard</h1>
                <p>Advanced analytics and predictions for leave management</p>
              </div>
            </div>

            <button className="btn btn-light" onClick={handleBack}>
              Back to Dashboard
            </button>
          </header>

          <div className="auth-info">
            <div>
              <p>
                Logged in as <strong>{credentials.username}</strong> ({credentials.role})
              </p>
              <button className="btn btn-light" onClick={fetchAiInsights} disabled={loading}>
                {loading ? "Loading..." : "Refresh Insights"}
              </button>
            </div>
          </div>

          <div className="insights-grid">
            {/* Trends Overview */}
            <section className="card insights-card">
              <h2>📊 Leave Trends Overview</h2>
              {aiInsights.trends && (
                <div className="insights-content">
                  <div className="stat-grid">
                    <div className="stat-item">
                      <span className="stat-number">{aiInsights.trends.totalLeaves || 0}</span>
                      <span className="stat-label">Total Leaves</span>
                    </div>
                    <div className="stat-item">
                      <span className="stat-number">{aiInsights.trends.approved || 0}</span>
                      <span className="stat-label">Approved</span>
                    </div>
                    <div className="stat-item">
                      <span className="stat-number">{aiInsights.trends.pending || 0}</span>
                      <span className="stat-label">Pending</span>
                    </div>
                    <div className="stat-item">
                      <span className="stat-number">{aiInsights.trends.rejected || 0}</span>
                      <span className="stat-label">Rejected</span>
                    </div>
                  </div>
                  <div className="approval-rate">
                    <h4>Approval Rate: {(aiInsights.trends.approvalRate || 0) * 100}%</h4>
                  </div>
                </div>
              )}
            </section>

            {/* Team Alerts */}
            <section className="card insights-card">
              <h2>⚠️ Team Shortage Alerts</h2>
              {aiInsights.teamAlerts && aiInsights.teamAlerts.length > 0 ? (
                <div className="alerts-list">
                  {aiInsights.teamAlerts.map((alert, index) => (
                    <div key={index} className={`alert alert-${alert.severity.toLowerCase()}`}>
                      <strong>{alert.date}:</strong> {alert.message}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="muted">No team shortage alerts</p>
              )}
            </section>

            {/* HR Risk Alerts */}
            <section className="card insights-card">
              <h2>🚨 HR Risk Alerts</h2>
              {aiInsights.hrAlerts && aiInsights.hrAlerts.length > 0 ? (
                <div className="alerts-list">
                  {aiInsights.hrAlerts.map((alert, index) => (
                    <div key={index} className={`alert alert-${alert.alertType.toLowerCase().includes('high') ? 'high' : 'medium'}`}>
                      <strong>{alert.employeeName}:</strong> {alert.message}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="muted">No HR risk alerts</p>
              )}
            </section>

            {/* Pattern Analysis */}
            <section className="card insights-card">
              <h2>🔍 Pattern Analysis</h2>
              {aiInsights.trends && (
                <div className="insights-content">
                  <h4>Most Popular Leave Days:</h4>
                  {aiInsights.trends.dayOfWeekStats && (
                    <ul>
                      {Object.entries(aiInsights.trends.dayOfWeekStats)
                        .sort(([,a], [,b]) => b - a)
                        .slice(0, 3)
                        .map(([day, count]) => (
                          <li key={day}>{day}: {count} leaves</li>
                        ))}
                    </ul>
                  )}

                  <h4>Leave Types Distribution:</h4>
                  {aiInsights.trends.leaveTypeStats && (
                    <ul>
                      {Object.entries(aiInsights.trends.leaveTypeStats)
                        .map(([type, count]) => (
                          <li key={type}>{type}: {count} leaves</li>
                        ))}
                    </ul>
                  )}
                </div>
              )}
            </section>
          </div>
        </div>
      ) : (
        <div className={`container ${currentPage === "leaves" ? "leaves-mode" : ""}`}>
          <header className="header">
            <div className="header-left">
              <button className="btn btn-back" onClick={handleBack}>
                ← Back
              </button>
              <div>
                <h1>Employee Leave Management</h1>
                <p>Apply, track and manage leave requests</p>
              </div>
            </div>

            <button className="btn btn-light" onClick={fetchLeaves} disabled={loading}>
              {loading ? "Loading..." : "Refresh"}
            </button>
          </header>

          <div className="auth-info">
            <div>
              <p>
                Logged in as <strong>{credentials.username}</strong> ({credentials.role})
              </p>
              <button className="btn btn-light" onClick={handleBack}>
                Back to Dashboard
              </button>
            </div>
            <button className="btn btn-light" onClick={handleLogout} disabled={loading}>
              Logout
            </button>
          </div>

          {credentials.role === "ADMIN" && (
            <div className="admin-banner">
              <h3>Admin Control Center</h3>
              <p>Review all requests instantly, plus use AI predictor for data-driven approvals.</p>
            </div>
          )}

          {credentials.role === "ADMIN" && (
            <div className="leave-summary">
              <button className="btn btn-primary" onClick={handleBack}>
                ← Back to Dashboard
              </button>
              <span>Total requests: {leaveStats.total}</span>
              <span>Approved: {leaveStats.approved}</span>
              <span>Rejected: {leaveStats.rejected}</span>
              <span>Pending: {leaveStats.pending}</span>
            </div>
          )}

          <div className={`grid ${credentials.role === "ADMIN" ? "admin-grid" : ""}`}>
            {/* Apply Form - Only for Employees */}
            {credentials.role === "EMPLOYEE" && (
              <section className="card">
                <h2>Apply Leave</h2>

                <form className="form" onSubmit={handleSubmit}>
                  <label>
                    Employee Name
                    <input
                      name="employeeName"
                      value={credentials.role === "EMPLOYEE" ? credentials.username : formData.employeeName}
                      onChange={handleChange}
                      required
                      disabled={credentials.role === "EMPLOYEE"}
                      placeholder={credentials.role === "EMPLOYEE" ? "(set from login)" : "Enter name"}
                    />
                  </label>

                  <label>
                    Leave Type
                    <select name="leaveType" value={formData.leaveType} onChange={handleChange}>
                      <option value="CASUAL">CASUAL</option>
                      <option value="SICK">SICK</option>
                      <option value="EARNED">EARNED</option>
                    </select>
                  </label>

                  <div className="row2">
                    <label>
                      Start Date
                      <input
                        type="date"
                        name="startDate"
                        value={formData.startDate}
                        onChange={handleChange}
                        required
                      />
                    </label>

                    <label>
                      End Date
                      <input
                        type="date"
                        name="endDate"
                        value={formData.endDate}
                        onChange={handleChange}
                        required
                      />
                    </label>
                  </div>

                  <label>
                    Reason
                    <textarea
                      name="reason"
                      value={formData.reason}
                      onChange={handleChange}
                      rows={3}
                      required
                    />
                  </label>

                  <button className="btn btn-primary" type="submit" disabled={loading}>
                    Apply
                  </button>
                </form>
              </section>
            )}

            {/* Leave Table */}
            <section className="card">
              <h2>{credentials.role === "ADMIN" ? "All Leave Requests" : "My Leave Requests"}</h2>
              <p className="muted" style={{ fontSize: '13px', marginBottom: '10px' }}>
                AI Predict is a suggestion tool for pending requests. Approved/Rejected requests are not evaluated.
              </p>

              <div className="tableWrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Employee</th>
                      <th>Type</th>
                      <th>Dates</th>
                      <th>Status</th>
                      <th className="actionsCol">Actions</th>
                    </tr>
                  </thead>

                  <tbody>
                    {loading ? (
                      <tr>
                        <td colSpan="6" className="muted">Loading...</td>
                      </tr>
                    ) : leaves.length === 0 ? (
                      <tr>
                        <td colSpan="6" className="muted">No requests yet.</td>
                      </tr>
                    ) : (
                      leaves.map((l) => (
                        <tr key={l.id}>
                          <td>{l.id}</td>
                          <td>{l.employeeName || "-"}</td>
                          <td>{l.leaveType || "-"}</td>
                          <td className="muted">
                            {l.startDate || "-"} → {l.endDate || "-"}
                          </td>
                          <td>
                            <span className={`badge ${String(l.status || "Pending").toLowerCase()}`}>
                              {l.status || "Pending"}
                            </span>
                          </td>

                          <td className="actions">
                            <button
                              className="btn btn-outline"
                              onClick={() => showAIPrediction(l)}
                              disabled={!isPending(l)}
                              title={!isPending(l) ? "Prediction only available for pending requests" : "Get AI prediction for this leave request"}
                            >
                              🤖 AI Predict
                            </button>

                            {credentials.role === "ADMIN" ? (
                              <>
                                <button className="btn btn-outline" onClick={() => updateStatus(l, "Approved")}>Approve</button>
                                <button className="btn btn-outline danger" onClick={() => updateStatus(l, "Rejected")}>Reject</button>
      
                                <button className="btn btn-light" onClick={() => deleteLeave(l.id)}>Delete</button>
                              </>
                            ) : (
                              <span className="muted">No admin actions</span>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          </div>

          <footer className="footer">
            <span>Application running on http://localhost:8080</span>
          </footer>
        </div>
      )}

      {/* AI Prediction Modal */}
      {aiPrediction && (
        <div className="modal-overlay" onClick={closeAIPrediction}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>🤖 AI Leave Analysis</h2>
              <button className="modal-close" onClick={closeAIPrediction}>×</button>
            </div>

            <div className="modal-body">
              <div className="prediction-summary">
                <h3>Leave Request Details</h3>
                <p><strong>Employee:</strong> {aiPrediction.leave.employeeName}</p>
                <p><strong>Type:</strong> {aiPrediction.leave.leaveType}</p>
                <p><strong>Dates:</strong> {aiPrediction.leave.startDate} → {aiPrediction.leave.endDate}</p>
                <p><strong>Reason:</strong> {aiPrediction.leave.reason}</p>
              </div>

              <div className="prediction-results">
                <h3>AI Analysis Results</h3>

                <div className="prediction-metric">
                  <span className="metric-label">Approval Probability:</span>
                  <span className="metric-value">{(aiPrediction.prediction.approvalProbability * 100).toFixed(1)}%</span>
                </div>

                <div className="prediction-metric">
                  <span className="metric-label">Confidence:</span>
                  <span className="metric-value">{(aiPrediction.prediction.confidence * 100).toFixed(1)}%</span>
                </div>

                <div className="prediction-recommendation">
                  <h4>AI Recommendation:</h4>
                  <div className={`recommendation-box ${aiPrediction.prediction.recommendation.includes('APPROVE') ? 'approve' : aiPrediction.prediction.recommendation.includes('REJECT') ? 'reject' : 'review'}`}>
                    {aiPrediction.prediction.recommendation}
                  </div>
                </div>

                {aiPrediction.prediction.patterns && (
                  <div className="prediction-section">
                    <h4>📊 Pattern Analysis:</h4>
                    <ul>
                      <li><strong>Pattern Type:</strong> {aiPrediction.prediction.patterns.patternType}</li>
                      {aiPrediction.prediction.patterns.mostCommonDay && (
                        <li><strong>Most Common Day:</strong> {aiPrediction.prediction.patterns.mostCommonDay} ({(aiPrediction.prediction.patterns.dayFrequency * 100).toFixed(1)}% of leaves)</li>
                      )}
                      <li><strong>Recent Activity:</strong> {aiPrediction.prediction.patterns.recentLeaves} leaves in last 30 days</li>
                    </ul>
                  </div>
                )}

                {aiPrediction.prediction.risks && (
                  <div className="prediction-section">
                    <h4>⚠️ Risk Assessment:</h4>
                    <div className={`risk-level risk-${aiPrediction.prediction.risks.riskLevel.toLowerCase()}`}>
                      Risk Level: {aiPrediction.prediction.risks.riskLevel}
                    </div>
                    <ul>
                      <li><strong>Risk Score:</strong> {(aiPrediction.prediction.risks.riskScore * 100).toFixed(1)}%</li>
                      {aiPrediction.prediction.risks.highUsage && <li>🚨 High leave usage detected</li>}
                      {aiPrediction.prediction.risks.suspiciousPattern && <li>🚨 Suspicious leave pattern</li>}
                      {aiPrediction.prediction.risks.teamShortage && <li>🚨 Potential team shortage</li>}
                    </ul>
                  </div>
                )}

                {aiPrediction.prediction.teamImpact && (
                  <div className="prediction-section">
                    <h4>👥 Team Impact:</h4>
                    <div className={`impact-level impact-${aiPrediction.prediction.teamImpact.impactLevel.toLowerCase()}`}>
                      Impact Level: {aiPrediction.prediction.teamImpact.impactLevel}
                    </div>
                    <ul>
                      <li><strong>Overlapping Leaves:</strong> {aiPrediction.prediction.teamImpact.totalOverlapping}</li>
                      {aiPrediction.prediction.teamImpact.teamShortageWarning && <li>⚠️ Team shortage warning</li>}
                    </ul>
                  </div>
                )}
              </div>
            </div>

            <div className="modal-footer">
              <button className="btn btn-light" onClick={closeAIPrediction}>Close</button>
            </div>
          </div>
        </div>
      )}
      </div> {/* main-container */}
    </div>
  );
}