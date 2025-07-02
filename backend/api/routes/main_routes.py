from flask import Blueprint, render_template, redirect, url_for
from flask_login import login_required, current_user

main_blueprint = Blueprint('main', __name__)

def admin_required(fn):
    """Decorador para restringir el acceso a rutas solo a administradores."""
    @login_required
    def wrapper(*args, **kwargs):
        if not current_user.is_authenticated or current_user.role != 'admin':
            return redirect(url_for('main.index')) 
        return fn(*args, **kwargs)
    wrapper.__name__ = fn.__name__
    return wrapper

@main_blueprint.route('/')
def index():
    if current_user.is_authenticated:
        return redirect(url_for('main.stored_keys_page'))
    return redirect(url_for('main.login_page'))

@main_blueprint.route('/login')
def login_page():
    return render_template('login.html')

@main_blueprint.route('/connection')
def connection_page():
    return render_template('connection.html')

@main_blueprint.route('/key_injection')
def key_injection_page():
    return render_template('key_injection.html')

@main_blueprint.route('/write_serial')
def write_serial_page():
    return render_template('write_serial.html')

@main_blueprint.route('/ceremony')
def ceremony_page():
    return render_template('ceremony.html')

@main_blueprint.route('/user_management')
@admin_required
def user_management_page():
    return render_template('user_management.html')

@main_blueprint.route('/stored_keys')
@login_required
def stored_keys_page():
    return render_template('stored_keys_mobile_optimized.html')

@main_blueprint.route('/profiles')
@login_required
def profiles_page():
    return render_template('profiles.html')

@main_blueprint.route('/injection_logs')
@login_required
def injection_logs_page():
    return render_template('injection_logs.html')

@main_blueprint.route('/pos_components_example')
@login_required
def pos_components_example():
    return render_template('example_pos_component.html')

@main_blueprint.route('/test-frontend')
def test_frontend():
    """Página de prueba para verificar el frontend"""
    with open('test_frontend.html', 'r', encoding='utf-8') as f:
        return f.read(), 200, {'Content-Type': 'text/html; charset=utf-8'} 